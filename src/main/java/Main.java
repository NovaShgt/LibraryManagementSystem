import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import entities.Book;
import entities.Borrow;
import entities.Card;
import queries.ApiResult;
import queries.BookQueryConditions;
import queries.BookQueryResults;
import queries.BorrowHistories;
import queries.CardList;
import utils.ConnectConfig;
import utils.DatabaseConnector;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static LibraryManagementSystem library;

    public static void main(String[] args) throws Exception {
        // 初始化数据库连接
        ConnectConfig conf = new ConnectConfig();
        log.info("Success to parse connect config. " + conf.toString());
        DatabaseConnector connector = new DatabaseConnector(conf);
        if (!connector.connect()) {
            log.severe("Failed to connect database.");
            System.exit(1);
        }
        library = new LibraryManagementSystemImpl(connector);
        log.info("Success to connect database.");

        // 创建 HTTP 服务器，监听 8000 端口
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/book/stock", new BookStockHandler()); // 必须在 /book 之前注册
        server.createContext("/book",       new BookHandler());
        server.createContext("/card",       new CardHandler());
        server.createContext("/borrow",     new BorrowHandler());
        server.start();
        log.info("Server is listening on port 8000");
    }

    /** 统一设置跨域响应头 */
    private static void setCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin",  "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
    }

    /** 读取请求体为字符串 */
    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    /** 发送 JSON 响应 */
    private static void sendJson(HttpExchange exchange, int statusCode, Object obj) throws IOException {
        String json = JSON.toJSONString(obj);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    /** 解析 URL 查询参数，如 ?cardId=1&name=foo */
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(
                        URLDecoder.decode(kv[0], "UTF-8"),
                        URLDecoder.decode(kv[1], "UTF-8")
                    );
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /** 处理 OPTIONS 预检请求 */
    private static void handleOptions(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }

    // card 路由
    static class CardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            try {
                switch (method) {
                    case "GET":    handleGet(exchange);    break;
                    case "POST":   handlePost(exchange);   break;
                    case "PUT":    handlePut(exchange);    break;
                    case "DELETE": handleDelete(exchange); break;
                    case "OPTIONS": handleOptions(exchange); break;
                    default: exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                sendJson(exchange, 500, new ApiResult(false, e.getMessage()));
            }
        }

        // GET /card → 查询所有借书证
        private void handleGet(HttpExchange exchange) throws IOException {
            ApiResult result = library.showCards();
            if (result.ok) {
                CardList cl = (CardList) result.payload;
                sendJson(exchange, 200, cl.getCards());
            } else {
                sendJson(exchange, 500, result);
            }
        }

        // POST /card → 新建借书证
        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            JSONObject json = JSON.parseObject(body);
            Card card = new Card();
            card.setName(json.getString("name"));
            card.setDepartment(json.getString("department"));
            // 前端传 "S"/"T" 或者 "学生"/"教师" 兼容处理
            String type = json.getString("type");
            card.setType("T".equals(type) || "教师".equals(type) ? Card.CardType.Teacher : Card.CardType.Student);
            ApiResult result = library.registerCard(card);
            sendJson(exchange, result.ok ? 200 : 400, result);
        }

        // PUT /card → 修改借书证（前端传 cardId + 新字段）
        private void handlePut(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            JSONObject json = JSON.parseObject(body);
            // 由于接口不支持修改借书证，这里用删除+新建模拟
            // 实际上 LibraryManagementSystem 接口没有 modifyCard，
            // 所以返回提示（如需支持可自行扩展接口）
            sendJson(exchange, 200, new ApiResult(false, "修改借书证信息功能暂不支持（接口未定义）"));
        }

        // DELETE /card?cardId=xxx → 删除借书证
        private void handleDelete(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            int cardId = Integer.parseInt(params.getOrDefault("cardId", "0"));
            ApiResult result = library.removeCard(cardId);
            sendJson(exchange, result.ok ? 200 : 400, result);
        }
    }

    // borrow 路由
    static class BorrowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            try {
                switch (method) {
                    case "GET":    handleGet(exchange);    break;
                    case "POST":   handlePost(exchange);   break;
                    case "DELETE": handleDelete(exchange); break;
                    case "OPTIONS": handleOptions(exchange); break;
                    default: exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                sendJson(exchange, 500, new ApiResult(false, e.getMessage()));
            }
        }

        // GET /borrow?cardID=xxx → 查询借书记录
        private void handleGet(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            int cardId = Integer.parseInt(params.getOrDefault("cardID", "0"));
            ApiResult result = library.showBorrowHistory(cardId);
            if (result.ok) {
                BorrowHistories bh = (BorrowHistories) result.payload;
                sendJson(exchange, 200, bh.getItems());
            } else {
                sendJson(exchange, 400, result);
            }
        }

        // POST /borrow → 借书
        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            JSONObject json = JSON.parseObject(body);
            Borrow borrow = new Borrow();
            borrow.setCardId(json.getIntValue("cardId"));
            borrow.setBookId(json.getIntValue("bookId"));
            borrow.setBorrowTime(System.currentTimeMillis());
            ApiResult result = library.borrowBook(borrow);
            sendJson(exchange, result.ok ? 200 : 400, result);
        }

        // DELETE /borrow → 还书
        private void handleDelete(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            JSONObject json = JSON.parseObject(body);
            Borrow borrow = new Borrow();
            borrow.setCardId(json.getIntValue("cardId"));
            borrow.setBookId(json.getIntValue("bookId"));
            borrow.setReturnTime(System.currentTimeMillis());
            ApiResult result = library.returnBook(borrow);
            sendJson(exchange, result.ok ? 200 : 400, result);
        }
    }

    // /book/stock 路由（必须在 /book 之前注册）
    static class BookStockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            try {
                if ("PATCH".equals(method)) {
                    String body = readBody(exchange);
                    JSONObject json = JSON.parseObject(body);
                    int bookId    = json.getIntValue("bookId");
                    int deltaStock = json.getIntValue("deltaStock");
                    ApiResult result = library.incBookStock(bookId, deltaStock);
                    sendJson(exchange, result.ok ? 200 : 400, result);
                } else if ("OPTIONS".equals(method)) {
                    handleOptions(exchange);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                sendJson(exchange, 500, new ApiResult(false, e.getMessage()));
            }
        }
    }

    // /book 路由
    static class BookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            try {
                switch (method) {
                    case "GET":    handleGet(exchange);    break;
                    case "POST":   handlePost(exchange);   break;
                    case "PUT":    handlePut(exchange);    break;
                    case "DELETE": handleDelete(exchange); break;
                    case "OPTIONS": handleOptions(exchange); break;
                    default: exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                sendJson(exchange, 500, new ApiResult(false, e.getMessage()));
            }
        }

        // GET /book?category=&title=&press=&author=&minPublishYear=&maxPublishYear=&minPrice=&maxPrice=
        private void handleGet(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            BookQueryConditions cond = new BookQueryConditions();

            if (params.containsKey("category"))       cond.setCategory(params.get("category"));
            if (params.containsKey("title"))          cond.setTitle(params.get("title"));
            if (params.containsKey("press"))          cond.setPress(params.get("press"));
            if (params.containsKey("author"))         cond.setAuthor(params.get("author"));
            if (params.containsKey("minPublishYear")) cond.setMinPublishYear(Integer.parseInt(params.get("minPublishYear")));
            if (params.containsKey("maxPublishYear")) cond.setMaxPublishYear(Integer.parseInt(params.get("maxPublishYear")));
            if (params.containsKey("minPrice"))       cond.setMinPrice(Double.parseDouble(params.get("minPrice")));
            if (params.containsKey("maxPrice"))       cond.setMaxPrice(Double.parseDouble(params.get("maxPrice")));
            if (params.containsKey("sortBy")) {
                for (Book.SortColumn sc : Book.SortColumn.values()) {
                    if (sc.getValue().equals(params.get("sortBy"))) {
                        cond.setSortBy(sc);
                        break;
                    }
                }
            }
            if ("desc".equalsIgnoreCase(params.get("sortOrder"))) {
                cond.setSortOrder(queries.SortOrder.DESC);
            }

            ApiResult result = library.queryBook(cond);
            if (result.ok) {
                BookQueryResults bqr = (BookQueryResults) result.payload;
                // 返回 {ok: true, results: [...]}
                Map<String, Object> resp = new HashMap<>();
                resp.put("ok", true);
                resp.put("results", bqr.getResults());
                sendJson(exchange, 200, resp);
            } else {
                sendJson(exchange, 400, result);
            }
        }

        // POST /book → 入库一本新书（body 为单个 Book JSON）
        //              或批量入库（body 为 Book JSON 数组）
        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            body = body.trim();
            ApiResult result;

            if (body.startsWith("[")) {
                // 批量入库
                List<Book> books = JSON.parseArray(body, Book.class);
                result = library.storeBook(books);
            } else {
                // 单本入库
                Book book = JSON.parseObject(body, Book.class);
                result = library.storeBook(book);
            }
            sendJson(exchange, result.ok ? 200 : 400, result);
        }

        // PUT /book → 修改图书信息
        private void handlePut(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            Book book = JSON.parseObject(body, Book.class);
            ApiResult result = library.modifyBookInfo(book);
            sendJson(exchange, result.ok ? 200 : 400, result);
        }

        // DELETE /book?bookId=xxx → 删除图书
        private void handleDelete(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            int bookId = Integer.parseInt(params.getOrDefault("bookId", "0"));
            ApiResult result = library.removeBook(bookId);
            sendJson(exchange, result.ok ? 200 : 400, result);
        }
    }
}
