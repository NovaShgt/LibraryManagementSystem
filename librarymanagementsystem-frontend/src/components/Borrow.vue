<template>
    <el-scrollbar height="100%" style="width: 100%;">

        <!-- 标题和搜索框 -->
        <div style="margin-top: 20px; margin-left: 40px; font-size: 2em; font-weight: bold;">
            借书记录查询
            <el-input v-model="toSearch" :prefix-icon="Search"
                style=" width: 15vw;min-width: 150px; margin-left: 30px; margin-right: 30px; float: right;"
                clearable />
        </div>

        <!-- 查询框 -->
        <div style="width:30%;margin:0 auto; padding-top:5vh;">
            <el-input v-model="toQuery" style="display:inline;" placeholder="输入借书证ID" />
            <el-button style="margin-left: 10px;" type="primary" @click="QueryBorrows">查询</el-button>
        </div>

        <!-- 结果表格 -->
        <el-table v-if="isShow" :data="fitlerTableData" height="600"
            :default-sort="{ prop: 'borrowTime', order: 'descending' }" :table-layout="'auto'"
            style="width: 100%; margin-left: 50px; margin-top: 30px; margin-right: 50px; max-width: 90vw;">
            <el-table-column prop="cardID" label="借书证ID" width="100" />
            <el-table-column prop="bookID" label="图书ID" sortable width="100" />
            <el-table-column prop="borrowTime" label="借出时间" sortable />
            <el-table-column prop="returnTime" label="归还时间" sortable />
        </el-table>

        <!-- 无结果提示 -->
        <div v-if="isShow && fitlerTableData.length === 0"
            style="text-align:center; margin-top:40px; color:#999;">
            暂无借书记录
        </div>

    </el-scrollbar>
</template>

<script>
import axios from 'axios'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

export default {
    data() {
        return {
            isShow: false,
            tableData: [],
            toQuery: '',
            toSearch: '',
            Search
        }
    },
    computed: {
        fitlerTableData() {
            return this.tableData.filter(
                (tuple) =>
                    (this.toSearch === '') ||
                    String(tuple.bookID).includes(this.toSearch) ||
                    tuple.borrowTime.toString().includes(this.toSearch) ||
                    tuple.returnTime.toString().includes(this.toSearch)
            )
        }
    },
    methods: {
        // 将 Unix 毫秒时间戳格式化为可读字符串
        formatTime(ts) {
            if (!ts || ts === 0) return '未归还'
            const d = new Date(ts)
            const pad = n => String(n).padStart(2, '0')
            return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
        },

        async QueryBorrows() {
            if (!this.toQuery) {
                ElMessage.warning('请输入借书证ID')
                return
            }
            this.tableData = []
            try {
                let response = await axios.get('/borrow', {
                    params: { cardID: this.toQuery }
                })
                let borrows = response.data
                borrows.forEach(borrow => {
                    this.tableData.push({
                        cardID: borrow.cardId,
                        bookID: borrow.bookId,
                        borrowTime: this.formatTime(borrow.borrowTime),
                        returnTime: this.formatTime(borrow.returnTime)
                    })
                })
                this.isShow = true
            } catch (e) {
                ElMessage.error('查询失败，请检查后端是否启动')
            }
        }
    }
}
</script>