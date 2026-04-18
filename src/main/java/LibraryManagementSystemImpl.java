import entities.Book;
import entities.Borrow;
import entities.Card;
import queries.*;
import utils.DBInitializer;
import utils.DatabaseConnector;

import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.util.List;

public class LibraryManagementSystemImpl implements LibraryManagementSystem {

    private final DatabaseConnector connector;

    public LibraryManagementSystemImpl(DatabaseConnector connector) {
        this.connector = connector;
    }

    @Override
    public ApiResult storeBook(Book book) {
        Connection conn = connector.getConn();
        try {
            // 检查是否已存在相同的书
            String checkSql = "SELECT book_id FROM book WHERE category = ? AND press = ? AND author = ? AND title = ? AND publish_year = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, book.getCategory());
            checkStmt.setString(2, book.getPress());
            checkStmt.setString(3, book.getAuthor());
            checkStmt.setString(4, book.getTitle());
            checkStmt.setInt(5, book.getPublishYear());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                return new ApiResult(false, "Book already exists in the library.");
            }

            // 插入新书，并获取自动生成的 book_id
            String insertSql = "INSERT INTO book (category, title, press, publish_year, author, price, stock) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, book.getCategory());
            insertStmt.setString(2, book.getTitle());
            insertStmt.setString(3, book.getPress());
            insertStmt.setInt(4, book.getPublishYear());
            insertStmt.setString(5, book.getAuthor());
            insertStmt.setDouble(6, book.getPrice());
            insertStmt.setInt(7, book.getStock());
            insertStmt.executeUpdate();

            // 将数据库生成的 book_id 写回 book 对象
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                book.setBookId(generatedKeys.getInt(1));
            }

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult incBookStock(int bookId, int deltaStock) {
        Connection conn = connector.getConn();
        try {
            // 查询当前库存，同时验证 bookId 是否存在
            String selectSql = "SELECT stock FROM book WHERE book_id = ?";
            PreparedStatement selectStmt = conn.prepareStatement(selectSql);
            selectStmt.setInt(1, bookId);
            ResultSet rs = selectStmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "Book not found: book_id = " + bookId);
            }

            int currentStock = rs.getInt("stock");
            if (currentStock + deltaStock < 0) {
                return new ApiResult(false, "Stock cannot be negative. Current stock: " + currentStock + ", delta: " + deltaStock);
            }

            // 更新库存
            String updateSql = "UPDATE book SET stock = stock + ? WHERE book_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, deltaStock);
            updateStmt.setInt(2, bookId);
            updateStmt.executeUpdate();

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult storeBook(List<Book> books) {
        Connection conn = connector.getConn();
        try {
            String insertSql = "INSERT INTO book (category, title, press, publish_year, author, price, stock) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);

            for (Book book : books) {
                insertStmt.setString(1, book.getCategory());
                insertStmt.setString(2, book.getTitle());
                insertStmt.setString(3, book.getPress());
                insertStmt.setInt(4, book.getPublishYear());
                insertStmt.setString(5, book.getAuthor());
                insertStmt.setDouble(6, book.getPrice());
                insertStmt.setInt(7, book.getStock());
                insertStmt.addBatch();
            }

            // 任意一条失败会抛异常触发 rollback
            insertStmt.executeBatch();

            // 将数据库生成的 book_id 依次写回每个 book 对象
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int index = 0;
            while (generatedKeys.next()) {
                books.get(index).setBookId(generatedKeys.getInt(1));
                index++;
            }

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult removeBook(int bookId) {
        Connection conn = connector.getConn();
        try {
            // 验证 bookId 是否存在
            String checkBookSql = "SELECT book_id FROM book WHERE book_id = ?";
            PreparedStatement checkBookStmt = conn.prepareStatement(checkBookSql);
            checkBookStmt.setInt(1, bookId);
            ResultSet rs = checkBookStmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "Book not found: book_id = " + bookId);
            }

            // 检查是否有人尚未归还该书
            String checkBorrowSql = "SELECT COUNT(*) FROM borrow WHERE book_id = ? AND return_time = 0";
            PreparedStatement checkBorrowStmt = conn.prepareStatement(checkBorrowSql);
            checkBorrowStmt.setInt(1, bookId);
            ResultSet borrowRs = checkBorrowStmt.executeQuery();
            if (borrowRs.next() && borrowRs.getInt(1) > 0) {
                return new ApiResult(false, "Cannot remove book: some copies have not been returned yet.");
            }

            // 安全删除
            String deleteSql = "DELETE FROM book WHERE book_id = ?";
            PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, bookId);
            deleteStmt.executeUpdate();

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult modifyBookInfo(Book book) {
        Connection conn = connector.getConn();
        try {
            // 验证 bookId 是否存在
            String checkSql = "SELECT book_id FROM book WHERE book_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, book.getBookId());
            ResultSet rs = checkStmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "Book not found: book_id = " + book.getBookId());
            }

            // 只更新 category/title/press/publish_year/author/price，不修改 book_id 和 stock
            String updateSql = "UPDATE book SET category = ?, title = ?, press = ?, publish_year = ?, author = ?, price = ? WHERE book_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setString(1, book.getCategory());
            updateStmt.setString(2, book.getTitle());
            updateStmt.setString(3, book.getPress());
            updateStmt.setInt(4, book.getPublishYear());
            updateStmt.setString(5, book.getAuthor());
            updateStmt.setDouble(6, book.getPrice());
            updateStmt.setInt(7, book.getBookId());
            updateStmt.executeUpdate();

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult queryBook(BookQueryConditions conditions) {
        Connection conn = connector.getConn();
        try {
            // 动态拼接 WHERE 子句
            StringBuilder sql = new StringBuilder("SELECT * FROM book WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (conditions.getCategory() != null) {
                sql.append(" AND category = ?");
                params.add(conditions.getCategory());
            }
            if (conditions.getTitle() != null) {
                sql.append(" AND title LIKE ?");
                params.add("%" + conditions.getTitle() + "%");
            }
            if (conditions.getPress() != null) {
                sql.append(" AND press LIKE ?");
                params.add("%" + conditions.getPress() + "%");
            }
            if (conditions.getMinPublishYear() != null) {
                sql.append(" AND publish_year >= ?");
                params.add(conditions.getMinPublishYear());
            }
            if (conditions.getMaxPublishYear() != null) {
                sql.append(" AND publish_year <= ?");
                params.add(conditions.getMaxPublishYear());
            }
            if (conditions.getAuthor() != null) {
                sql.append(" AND author LIKE ?");
                params.add("%" + conditions.getAuthor() + "%");
            }
            if (conditions.getMinPrice() != null) {
                sql.append(" AND price >= ?");
                params.add(conditions.getMinPrice());
            }
            if (conditions.getMaxPrice() != null) {
                sql.append(" AND price <= ?");
                params.add(conditions.getMaxPrice());
            }

            // 排序：先按指定字段，同等条件下按 book_id ASC
            sql.append(" ORDER BY ")
            .append(conditions.getSortBy().getValue())
            .append(" ")
            .append(conditions.getSortOrder().getValue())
            .append(", book_id ASC");

            PreparedStatement stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            List<Book> books = new ArrayList<>();
            while (rs.next()) {
                Book book = new Book();
                book.setBookId(rs.getInt("book_id"));
                book.setCategory(rs.getString("category"));
                book.setTitle(rs.getString("title"));
                book.setPress(rs.getString("press"));
                book.setPublishYear(rs.getInt("publish_year"));
                book.setAuthor(rs.getString("author"));
                book.setPrice(rs.getDouble("price"));
                book.setStock(rs.getInt("stock"));
                books.add(book);
            }

            commit(conn);
            return new ApiResult(true, null, new BookQueryResults(books));
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult borrowBook(Borrow borrow) {
        Connection conn = connector.getConn();
        try {
            // 检查库存是否充足
            String stockSql = "SELECT stock FROM book WHERE book_id = ?";
            PreparedStatement stockStmt = conn.prepareStatement(stockSql);
            stockStmt.setInt(1, borrow.getBookId());
            ResultSet stockRs = stockStmt.executeQuery();
            if (!stockRs.next()) {
                return new ApiResult(false, "Book not found: book_id = " + borrow.getBookId());
            }
            if (stockRs.getInt("stock") <= 0) {
                return new ApiResult(false, "No stock available for book_id = " + borrow.getBookId());
            }

            // 检查该用户是否已借了这本书且未归还
            String borrowCheckSql = "SELECT COUNT(*) FROM borrow WHERE card_id = ? AND book_id = ? AND return_time = 0";
            PreparedStatement borrowCheckStmt = conn.prepareStatement(borrowCheckSql);
            borrowCheckStmt.setInt(1, borrow.getCardId());
            borrowCheckStmt.setInt(2, borrow.getBookId());
            ResultSet borrowCheckRs = borrowCheckStmt.executeQuery();
            if (borrowCheckRs.next() && borrowCheckRs.getInt(1) > 0) {
                return new ApiResult(false, "This card has already borrowed this book and not returned yet.");
            }

            // 插入借阅记录
            String insertSql = "INSERT INTO borrow (card_id, book_id, borrow_time, return_time) VALUES (?, ?, ?, 0)";
            PreparedStatement insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setInt(1, borrow.getCardId());
            insertStmt.setInt(2, borrow.getBookId());
            insertStmt.setLong(3, borrow.getBorrowTime());
            insertStmt.executeUpdate();

            // 库存减 1
            String updateStockSql = "UPDATE book SET stock = stock - 1 WHERE book_id = ?";
            PreparedStatement updateStockStmt = conn.prepareStatement(updateStockSql);
            updateStockStmt.setInt(1, borrow.getBookId());
            updateStockStmt.executeUpdate();

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult returnBook(Borrow borrow) {
        Connection conn = connector.getConn();
        try {
            // 查找该用户对该书的未归还记录
            String checkSql = "SELECT borrow_time FROM borrow WHERE card_id = ? AND book_id = ? AND return_time = 0";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, borrow.getCardId());
            checkStmt.setInt(2, borrow.getBookId());
            ResultSet rs = checkStmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "No active borrow record found for this card and book.");
            }

            // 更新归还时间
            String updateBorrowSql = "UPDATE borrow SET return_time = ? WHERE card_id = ? AND book_id = ? AND return_time = 0";
            PreparedStatement updateBorrowStmt = conn.prepareStatement(updateBorrowSql);
            updateBorrowStmt.setLong(1, borrow.getReturnTime());
            updateBorrowStmt.setInt(2, borrow.getCardId());
            updateBorrowStmt.setInt(3, borrow.getBookId());
            updateBorrowStmt.executeUpdate();

            // 库存加 1
            String updateStockSql = "UPDATE book SET stock = stock + 1 WHERE book_id = ?";
            PreparedStatement updateStockStmt = conn.prepareStatement(updateStockSql);
            updateStockStmt.setInt(1, borrow.getBookId());
            updateStockStmt.executeUpdate();

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult showBorrowHistory(int cardId) {
        Connection conn = connector.getConn();
        try {
            // JOIN borrow 和 book，并排序
            String sql = "SELECT b.card_id, bk.book_id, bk.category, bk.title, bk.press, " +
                        "bk.publish_year, bk.author, bk.price, b.borrow_time, b.return_time " +
                        "FROM borrow b JOIN book bk ON b.book_id = bk.book_id " +
                        "WHERE b.card_id = ? " +
                        "ORDER BY b.borrow_time DESC, bk.book_id ASC";

            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, cardId);
            ResultSet rs = stmt.executeQuery();

            List<BorrowHistories.Item> items = new ArrayList<>();
            while (rs.next()) {
                BorrowHistories.Item item = new BorrowHistories.Item();
                item.setCardId(rs.getInt("card_id"));
                item.setBookId(rs.getInt("book_id"));
                item.setCategory(rs.getString("category"));
                item.setTitle(rs.getString("title"));
                item.setPress(rs.getString("press"));
                item.setPublishYear(rs.getInt("publish_year"));
                item.setAuthor(rs.getString("author"));
                item.setPrice(rs.getDouble("price"));
                item.setBorrowTime(rs.getLong("borrow_time"));
                item.setReturnTime(rs.getLong("return_time"));
                items.add(item);
            }

            commit(conn);
            return new ApiResult(true, null, new BorrowHistories(items));
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult registerCard(Card card) {
        Connection conn = connector.getConn();
        try {
            // 检查是否已存在相同的借书证
            String checkSql = "SELECT card_id FROM card WHERE name = ? AND department = ? AND type = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, card.getName());
            checkStmt.setString(2, card.getDepartment());
            checkStmt.setString(3, card.getType().getStr());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                return new ApiResult(false, "Card already exists.");
            }

            // 插入借书证，并获取自动生成的 card_id
            String insertSql = "INSERT INTO card (name, department, type) VALUES (?, ?, ?)";
            PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, card.getName());
            insertStmt.setString(2, card.getDepartment());
            insertStmt.setString(3, card.getType().getStr());
            insertStmt.executeUpdate();

            // 将数据库生成的 card_id 写回 card 对象
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                card.setCardId(generatedKeys.getInt(1));
            }

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult removeCard(int cardId) {
        Connection conn = connector.getConn();
        try {
            String checkCardSql = "SELECT card_id FROM card WHERE card_id = ?";
            PreparedStatement checkCardStmt = conn.prepareStatement(checkCardSql);
            checkCardStmt.setInt(1, cardId);
            ResultSet rs = checkCardStmt.executeQuery();
            if (!rs.next()) {
                return new ApiResult(false, "Card not found: card_id = " + cardId);
            }

            String checkBorrowSql = "SELECT COUNT(*) FROM borrow WHERE card_id = ? AND return_time = 0";
            PreparedStatement checkBorrowStmt = conn.prepareStatement(checkBorrowSql);
            checkBorrowStmt.setInt(1, cardId);
            ResultSet borrowRs = checkBorrowStmt.executeQuery();
            if (borrowRs.next() && borrowRs.getInt(1) > 0) {
                return new ApiResult(false, "Cannot remove card: there are unreturned books.");
            }

            String deleteSql = "DELETE FROM card WHERE card_id = ?";
            PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, cardId);
            deleteStmt.executeUpdate();

            commit(conn);
            return new ApiResult(true, null);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult showCards() {
        Connection conn = connector.getConn();
        try {
            String sql = "SELECT * FROM card ORDER BY card_id ASC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            List<Card> cards = new ArrayList<>();
            while (rs.next()) {
                Card card = new Card();
                card.setCardId(rs.getInt("card_id"));
                card.setName(rs.getString("name"));
                card.setDepartment(rs.getString("department"));
                card.setType(Card.CardType.values(rs.getString("type")));
                cards.add(card);
            }

            commit(conn);
            return new ApiResult(true, null, new CardList(cards));
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
    }

    @Override
    public ApiResult resetDatabase() {
        Connection conn = connector.getConn();
        try {
            Statement stmt = conn.createStatement();
            DBInitializer initializer = connector.getConf().getType().getDbInitializer();
            stmt.addBatch(initializer.sqlDropBorrow());
            stmt.addBatch(initializer.sqlDropBook());
            stmt.addBatch(initializer.sqlDropCard());
            stmt.addBatch(initializer.sqlCreateCard());
            stmt.addBatch(initializer.sqlCreateBook());
            stmt.addBatch(initializer.sqlCreateBorrow());
            stmt.executeBatch();
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }

    private void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void commit(Connection conn) {
        try {
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
