<!-- TODO: YOUR CODE HERE -->
<template>
    <el-scrollbar height="100%" style="width: 100%;">

        <!-- 标题 -->
        <div style="margin-top: 20px; margin-left: 40px; font-size: 2em; font-weight: bold;">图书管理</div>

        <!-- 查询条件栏 -->
        <div style="margin: 16px 40px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center;">
            <el-input v-model="query.category"       placeholder="分类（精确）"   style="width:120px;" clearable />
            <el-input v-model="query.title"          placeholder="书名（模糊）"   style="width:120px;" clearable />
            <el-input v-model="query.press"          placeholder="出版社（模糊）" style="width:120px;" clearable />
            <el-input v-model="query.author"         placeholder="作者（模糊）"   style="width:120px;" clearable />
            <el-input v-model="query.minPublishYear" placeholder="出版年≥"       style="width:90px;"  clearable />
            <el-input v-model="query.maxPublishYear" placeholder="出版年≤"       style="width:90px;"  clearable />
            <el-input v-model="query.minPrice"       placeholder="价格≥"         style="width:78px;"  clearable />
            <el-input v-model="query.maxPrice"       placeholder="价格≤"         style="width:78px;"  clearable />
            <el-select v-model="query.sortBy" style="width:105px;">
                <el-option v-for="col in sortColumns" :key="col.value" :label="col.label" :value="col.value" />
            </el-select>
            <el-select v-model="query.sortOrder" style="width:85px;">
                <el-option label="升序" value="asc" />
                <el-option label="降序" value="desc" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="QueryBooks">查询</el-button>
            <el-button @click="ResetQuery">重置</el-button>
            <div style="margin-left:auto; display:flex; gap:8px;">
                <el-button type="success"  :icon="Plus"   @click="openNewBookDialog">入库新书</el-button>
                <el-button type="warning"  :icon="Upload"  @click="openBatchDialog">批量入库</el-button>
                <el-button type="primary"                  @click="openBorrowDialog">借书</el-button>
                <el-button type="info"                     @click="openReturnDialog">还书</el-button>
            </div>
        </div>

        <!-- 图书表格 -->
        <el-table :data="books" style="width: calc(100% - 80px); margin: 0 40px;" border stripe>
            <el-table-column prop="bookId"      label="ID"     width="65"      sortable />
            <el-table-column prop="category"    label="分类"   width="110" />
            <el-table-column prop="title"       label="书名"   min-width="150" />
            <el-table-column prop="press"       label="出版社" width="115" />
            <el-table-column prop="publishYear" label="年份"   width="78"      sortable />
            <el-table-column prop="author"      label="作者"   width="100" />
            <el-table-column prop="price"       label="价格"   width="85"      sortable>
                <template #default="scope">¥{{ scope.row.price.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="stock"       label="库存"   width="72"      sortable />
            <el-table-column label="操作" width="200" fixed="right">
                <template #default="scope">
                    <el-button size="small" type="primary" @click="openModifyDialog(scope.row)">修改</el-button>
                    <el-button size="small" type="warning" @click="openStockDialog(scope.row)">库存</el-button>
                    <el-button size="small" type="danger"  @click="openRemoveDialog(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <div style="margin:10px 40px; color:#999;">共 {{ books.length }} 条记录</div>


        <!-- 入库新书 -->
        <el-dialog v-model="newBookVisible" title="入库新书" width="420px" align-center>
            <el-form :model="newBookInfo" label-width="80px" style="padding-right:20px;">
                <el-form-item label="分类">     <el-input v-model="newBookInfo.category"    clearable /></el-form-item>
                <el-form-item label="书名">     <el-input v-model="newBookInfo.title"       clearable /></el-form-item>
                <el-form-item label="出版社">   <el-input v-model="newBookInfo.press"       clearable /></el-form-item>
                <el-form-item label="出版年份"> <el-input v-model="newBookInfo.publishYear" clearable /></el-form-item>
                <el-form-item label="作者">     <el-input v-model="newBookInfo.author"      clearable /></el-form-item>
                <el-form-item label="价格">     <el-input v-model="newBookInfo.price"       clearable /></el-form-item>
                <el-form-item label="初始库存"> <el-input v-model="newBookInfo.stock"       clearable /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="newBookVisible = false">取消</el-button>
                <el-button type="primary" @click="ConfirmNewBook"
                    :disabled="!newBookInfo.category||!newBookInfo.title||!newBookInfo.press||!newBookInfo.author">
                    确定入库
                </el-button>
            </template>
        </el-dialog>


        <!-- 批量入库 -->
        <el-dialog v-model="batchImportVisible" title="批量入库（JSON 文件）" width="500px" align-center>
            <div style="color:#666; font-size:13px; margin-bottom:10px;">
                上传包含图书数组的 JSON 文件，格式：
                <pre style="background:#f5f5f5; padding:8px; border-radius:4px; font-size:12px; margin-top:6px; overflow:auto;">[{"category":"CS","title":"DB","press":"Press-A","publishYear":2020,"author":"Bob","price":59.9,"stock":5}, ...]</pre>
            </div>
            <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                accept=".json"
                :on-change="onFileChange"
                :on-exceed="() => $message.warning('每次只能选一个文件')"
                drag>
                <el-icon style="font-size:36px; color:#409eff; margin-top:10px;"><Upload /></el-icon>
                <div style="margin:8px 0 10px;">将 .json 文件拖到此处，或 <em>点击选择</em></div>
            </el-upload>
            <div v-if="parsedBooks.length > 0" style="margin-top:10px; color:#409eff; font-weight:bold;">
                已解析 {{ parsedBooks.length }} 本图书，点击确定执行批量入库。
            </div>
            <div v-if="parseError" style="margin-top:8px; color:#f56c6c;">{{ parseError }}</div>
            <template #footer>
                <el-button @click="closeBatchDialog">取消</el-button>
                <el-button type="primary" :disabled="parsedBooks.length === 0" @click="ConfirmBatchImport">
                    批量入库{{ parsedBooks.length > 0 ? '（' + parsedBooks.length + ' 本）' : '' }}
                </el-button>
            </template>
        </el-dialog>


        <!-- 修改图书 -->
        <el-dialog v-model="modifyBookVisible" :title="'修改图书 ID: ' + modifyBookInfo.bookId" width="420px" align-center>
            <el-form :model="modifyBookInfo" label-width="80px" style="padding-right:20px;">
                <el-form-item label="分类">     <el-input v-model="modifyBookInfo.category"    clearable /></el-form-item>
                <el-form-item label="书名">     <el-input v-model="modifyBookInfo.title"       clearable /></el-form-item>
                <el-form-item label="出版社">   <el-input v-model="modifyBookInfo.press"       clearable /></el-form-item>
                <el-form-item label="出版年份"> <el-input v-model="modifyBookInfo.publishYear" clearable /></el-form-item>
                <el-form-item label="作者">     <el-input v-model="modifyBookInfo.author"      clearable /></el-form-item>
                <el-form-item label="价格">     <el-input v-model="modifyBookInfo.price"       clearable /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="modifyBookVisible = false">取消</el-button>
                <el-button type="primary" @click="ConfirmModifyBook">确定修改</el-button>
            </template>
        </el-dialog>


        <!-- 调整库存 -->
        <el-dialog v-model="stockVisible"
            :title="'调整库存：' + stockInfo.title + '（当前 ' + stockInfo.currentStock + '）'"
            width="360px" align-center>
            <div style="display:flex;align-items:center;gap:10px;padding:12px 0;">
                <span style="white-space:nowrap;">变化量（正增负减）：</span>
                <el-input-number v-model="stockInfo.delta" :min="-stockInfo.currentStock" style="width:140px;" />
            </div>
            <template #footer>
                <el-button @click="stockVisible = false">取消</el-button>
                <el-button type="primary" @click="ConfirmIncStock">确定</el-button>
            </template>
        </el-dialog>


        <!-- 删除图书 -->
        <el-dialog v-model="removeBookVisible" title="确认删除" width="320px">
            <span>确定删除《<b>{{ toRemoveBook.title }}</b>》(ID: {{ toRemoveBook.bookId }})？</span>
            <template #footer>
                <el-button @click="removeBookVisible = false">取消</el-button>
                <el-button type="danger" @click="ConfirmRemoveBook">删除</el-button>
            </template>
        </el-dialog>


        <!-- 借书 -->
        <el-dialog v-model="borrowVisible" title="借书" width="340px" align-center>
            <el-form label-width="90px" style="padding-right:20px;">
                <el-form-item label="借书证ID"><el-input v-model="borrowInfo.cardId" clearable /></el-form-item>
                <el-form-item label="图书ID">  <el-input v-model="borrowInfo.bookId" clearable /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="borrowVisible = false">取消</el-button>
                <el-button type="primary" @click="ConfirmBorrow">确认借书</el-button>
            </template>
        </el-dialog>


        <!-- 还书 -->
        <el-dialog v-model="returnVisible" title="还书" width="340px" align-center>
            <el-form label-width="90px" style="padding-right:20px;">
                <el-form-item label="借书证ID"><el-input v-model="returnInfo.cardId" clearable /></el-form-item>
                <el-form-item label="图书ID">  <el-input v-model="returnInfo.bookId" clearable /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="returnVisible = false">取消</el-button>
                <el-button type="primary" @click="ConfirmReturn">确认还书</el-button>
            </template>
        </el-dialog>

    </el-scrollbar>
</template>

<script>
import { Search, Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

export default {
    data() {
        return {
            Search, Plus, Upload,
            books: [],
            query: {
                category:'', title:'', press:'', author:'',
                minPublishYear:'', maxPublishYear:'', minPrice:'', maxPrice:'',
                sortBy:'book_id', sortOrder:'asc'
            },
            sortColumns: [
                { label:'ID',     value:'book_id'      },
                { label:'分类',   value:'category'     },
                { label:'书名',   value:'title'        },
                { label:'出版社', value:'press'        },
                { label:'年份',   value:'publish_year' },
                { label:'作者',   value:'author'       },
                { label:'价格',   value:'price'        },
                { label:'库存',   value:'stock'        },
            ],
            newBookVisible:    false,
            newBookInfo:       { category:'', title:'', press:'', publishYear:'', author:'', price:'', stock:'' },
            batchImportVisible: false,
            fileList:          [],
            parsedBooks:       [],
            parseError:        '',
            modifyBookVisible: false,
            modifyBookInfo:    { bookId:0, category:'', title:'', press:'', publishYear:'', author:'', price:'' },
            stockVisible:      false,
            stockInfo:         { bookId:0, title:'', currentStock:0, delta:0 },
            removeBookVisible: false,
            toRemoveBook:      { bookId:0, title:'' },
            borrowVisible:     false,
            borrowInfo:        { cardId:'', bookId:'' },
            returnVisible:     false,
            returnInfo:        { cardId:'', bookId:'' },
        }
    },
    methods: {
        QueryBooks() {
            const params = {}
            if (this.query.category)       params.category       = this.query.category
            if (this.query.title)          params.title          = this.query.title
            if (this.query.press)          params.press          = this.query.press
            if (this.query.author)         params.author         = this.query.author
            if (this.query.minPublishYear) params.minPublishYear = parseInt(this.query.minPublishYear)
            if (this.query.maxPublishYear) params.maxPublishYear = parseInt(this.query.maxPublishYear)
            if (this.query.minPrice)       params.minPrice       = parseFloat(this.query.minPrice)
            if (this.query.maxPrice)       params.maxPrice       = parseFloat(this.query.maxPrice)
            params.sortBy    = this.query.sortBy
            params.sortOrder = this.query.sortOrder
            axios.get('/book', { params }).then(res => {
                if (res.data.ok) this.books = res.data.results
                else ElMessage.error('查询失败：' + res.data.message)
            }).catch(() => ElMessage.error('请求失败，请检查后端'))
        },
        ResetQuery() {
            this.query = { category:'', title:'', press:'', author:'',
                minPublishYear:'', maxPublishYear:'', minPrice:'', maxPrice:'',
                sortBy:'book_id', sortOrder:'asc' }
            this.QueryBooks()
        },

        // ── 入库新书 ──
        openNewBookDialog() {
            this.newBookInfo = { category:'', title:'', press:'', publishYear:'', author:'', price:'', stock:'' }
            this.newBookVisible = true
        },
        ConfirmNewBook() {
            axios.post('/book', {
                category:    this.newBookInfo.category,
                title:       this.newBookInfo.title,
                press:       this.newBookInfo.press,
                publishYear: parseInt(this.newBookInfo.publishYear) || 0,
                author:      this.newBookInfo.author,
                price:       parseFloat(this.newBookInfo.price) || 0,
                stock:       parseInt(this.newBookInfo.stock) || 0
            }).then(res => {
                if (res.data.ok) { ElMessage.success('入库成功'); this.newBookVisible = false; this.QueryBooks() }
                else ElMessage.error('入库失败：' + res.data.message)
            }).catch(() => ElMessage.error('请求失败'))
        },

        // ── 批量入库 ──
        openBatchDialog() {
            this.fileList = []; this.parsedBooks = []; this.parseError = ''
            this.batchImportVisible = true
        },
        closeBatchDialog() {
            this.batchImportVisible = false; this.fileList = []; this.parsedBooks = []; this.parseError = ''
        },
        onFileChange(file) {
            this.parsedBooks = []; this.parseError = ''
            const reader = new FileReader()
            reader.onload = (e) => {
                try {
                    const arr = JSON.parse(e.target.result)
                    if (!Array.isArray(arr)) { this.parseError = 'JSON 格式错误：根元素必须是数组 [...]'; return }
                    this.parsedBooks = arr
                } catch (err) {
                    this.parseError = 'JSON 解析失败：' + err.message
                }
            }
            reader.readAsText(file.raw, 'utf-8')
        },
        ConfirmBatchImport() {
            axios.post('/book', this.parsedBooks).then(res => {
                if (res.data.ok) {
                    ElMessage.success(`批量入库成功，共 ${this.parsedBooks.length} 本`)
                    this.closeBatchDialog(); this.QueryBooks()
                } else {
                    ElMessage.error('批量入库失败：' + res.data.message)
                }
            }).catch(() => ElMessage.error('请求失败'))
        },

        // ── 修改图书 ──
        openModifyDialog(row) { this.modifyBookInfo = { ...row }; this.modifyBookVisible = true },
        ConfirmModifyBook() {
            axios.put('/book', {
                bookId:      this.modifyBookInfo.bookId,
                category:    this.modifyBookInfo.category,
                title:       this.modifyBookInfo.title,
                press:       this.modifyBookInfo.press,
                publishYear: parseInt(this.modifyBookInfo.publishYear),
                author:      this.modifyBookInfo.author,
                price:       parseFloat(this.modifyBookInfo.price)
            }).then(res => {
                if (res.data.ok) { ElMessage.success('修改成功'); this.modifyBookVisible = false; this.QueryBooks() }
                else ElMessage.error('修改失败：' + res.data.message)
            }).catch(() => ElMessage.error('请求失败'))
        },

        // ── 调整库存 ──
        openStockDialog(row) {
            this.stockInfo = { bookId: row.bookId, title: row.title, currentStock: row.stock, delta: 0 }
            this.stockVisible = true
        },
        ConfirmIncStock() {
            if (this.stockInfo.delta === 0) { ElMessage.warning('变化量为 0'); return }
            axios.patch('/book/stock', { bookId: this.stockInfo.bookId, deltaStock: this.stockInfo.delta })
                .then(res => {
                    if (res.data.ok) { ElMessage.success('库存调整成功'); this.stockVisible = false; this.QueryBooks() }
                    else ElMessage.error('调整失败：' + res.data.message)
                }).catch(() => ElMessage.error('请求失败'))
        },

        // ── 删除图书 ──
        openRemoveDialog(row) { this.toRemoveBook = { bookId: row.bookId, title: row.title }; this.removeBookVisible = true },
        ConfirmRemoveBook() {
            axios.delete('/book', { params: { bookId: this.toRemoveBook.bookId } }).then(res => {
                if (res.data.ok) { ElMessage.success('删除成功'); this.removeBookVisible = false; this.QueryBooks() }
                else ElMessage.error('删除失败：' + res.data.message)
            }).catch(() => ElMessage.error('请求失败'))
        },

        // ── 借书 ──
        openBorrowDialog() { this.borrowInfo = { cardId:'', bookId:'' }; this.borrowVisible = true },
        ConfirmBorrow() {
            axios.post('/borrow', { cardId: parseInt(this.borrowInfo.cardId), bookId: parseInt(this.borrowInfo.bookId) })
                .then(res => {
                    if (res.data.ok) { ElMessage.success('借书成功'); this.borrowVisible = false; this.QueryBooks() }
                    else ElMessage.error('借书失败：' + res.data.message)
                }).catch(() => ElMessage.error('请求失败'))
        },

        // ── 还书 ──
        openReturnDialog() { this.returnInfo = { cardId:'', bookId:'' }; this.returnVisible = true },
        ConfirmReturn() {
            axios.delete('/borrow', { data: { cardId: parseInt(this.returnInfo.cardId), bookId: parseInt(this.returnInfo.bookId) } })
                .then(res => {
                    if (res.data.ok) { ElMessage.success('还书成功'); this.returnVisible = false; this.QueryBooks() }
                    else ElMessage.error('还书失败：' + res.data.message)
                }).catch(() => ElMessage.error('请求失败'))
        },
    },
    mounted() { this.QueryBooks() }
}
</script>