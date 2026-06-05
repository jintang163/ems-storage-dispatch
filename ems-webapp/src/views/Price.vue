<template>
  <div class="price-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">电价配置</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增电价
        </el-button>
      </div>

      <el-alert
        :title="`当前电价时段：${currentPrice?.periodName || '--'}，电价：${currentPrice?.price || '--'} 元/kWh`"
        type="info"
        show-icon
        style="margin-bottom: 20px"
      />

      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="periodName" label="时段名称" width="120" />
        <el-table-column prop="periodType" label="时段类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getPeriodTypeTag(row.periodType)" size="small">
              {{ row.periodType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时段范围" width="200">
          <template #default="{ row }">
            {{ row.startTime }} - {{ row.endTime }}
          </template>
        </el-table-column>
        <el-table-column prop="price" label="电价 (元/kWh)" width="140">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: 600;">{{ row.price.toFixed(4) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="时段名称" prop="periodName">
          <el-input v-model="formData.periodName" placeholder="请输入时段名称" />
        </el-form-item>
        <el-form-item label="时段类型" prop="periodType">
          <el-select v-model="formData.periodType" placeholder="请选择时段类型" style="width: 100%">
            <el-option label="尖峰" value="尖峰" />
            <el-option label="高峰" value="高峰" />
            <el-option label="平段" value="平段" />
            <el-option label="低谷" value="低谷" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-select
            v-model="formData.startTime"
            :picker-options="{ start: '00:00', step: '00:30', end: '23:30' }"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-select
            v-model="formData.endTime"
            :picker-options="{ start: '00:00', step: '00:30', end: '23:59' }"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="电价" prop="price">
          <el-input-number
            v-model="formData.price"
            :min="0"
            :max="10"
            :step="0.0001"
            :precision="4"
            style="width: 100%"
          />
          <span style="color: #909399; font-size: 12px;">单位：元/kWh</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { priceApi, type TimeOfUsePrice } from '@/api'

const tableData = ref<TimeOfUsePrice[]>([])
const currentPrice = ref<TimeOfUsePrice | null>(null)
const dialogVisible = ref(false)
const dialogTitle = ref('新增电价')
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive<Partial<TimeOfUsePrice>>({
  id: undefined,
  periodName: '',
  periodType: '平段',
  startTime: '08:00',
  endTime: '12:00',
  price: 0.6,
  status: 1
})

const formRules: FormRules = {
  periodName: [{ required: true, message: '请输入时段名称', trigger: 'blur' }],
  periodType: [{ required: true, message: '请选择时段类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  price: [{ required: true, message: '请输入电价', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const getPeriodTypeTag = (type: string) => {
  const tagMap: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
    尖峰: 'danger',
    高峰: 'warning',
    平段: 'info',
    低谷: 'success'
  }
  return tagMap[type] || 'info'
}

const loadData = async () => {
  try {
    const [list, current] = await Promise.all([
      priceApi.list(),
      priceApi.getCurrentPrice().catch(() => null)
    ])
    tableData.value = list
    currentPrice.value = current
  } catch (e) {
    ElMessage.error('加载数据失败')
  }
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增电价'
  Object.assign(formData, {
    id: undefined,
    periodName: '',
    periodType: '平段',
    startTime: '08:00',
    endTime: '12:00',
    price: 0.6,
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: TimeOfUsePrice) => {
  isEdit.value = true
  dialogTitle.value = '编辑电价'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: TimeOfUsePrice) => {
  ElMessageBox.confirm('确定要删除该电价配置吗？', '提示', {
    type: 'warning'
  })
    .then(async () => {
      await priceApi.delete(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await priceApi.update(formData as TimeOfUsePrice)
          ElMessage.success('更新成功')
        } else {
          await priceApi.create(formData)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadData()
      } catch (e) {
        ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
      }
    }
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
}
</style>
