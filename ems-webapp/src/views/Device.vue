<template>
  <div class="device-page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">设备管理</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增设备
        </el-button>
      </div>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="设备编号">
          <el-input v-model="queryForm.deviceSn" placeholder="请输入设备编号" clearable />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="queryForm.name" placeholder="请输入设备名称" clearable />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="queryForm.deviceTypeId" placeholder="请选择设备类型" clearable>
            <el-option
              v-for="type in deviceTypes"
              :key="type.id"
              :label="type.name"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="deviceSn" label="设备编号" width="140" />
        <el-table-column prop="name" label="设备名称" width="140" />
        <el-table-column prop="deviceTypeName" label="设备类型" width="100" />
        <el-table-column prop="protocol" label="协议" width="100" />
        <el-table-column label="连接信息" width="180">
          <template #default="{ row }">
            {{ row.host }}:{{ row.port }} (Slave: {{ row.slaveId }})
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="120" />
        <el-table-column label="采样间隔" width="100">
          <template #default="{ row }">
            {{ row.samplingInterval }}ms
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="设备编号" prop="deviceSn">
          <el-input v-model="formData.deviceSn" placeholder="请输入设备编号" />
        </el-form-item>
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备类型" prop="deviceTypeId">
          <el-select v-model="formData.deviceTypeId" placeholder="请选择设备类型" style="width: 100%">
            <el-option
              v-for="type in deviceTypes"
              :key="type.id"
              :label="type.name"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="协议" prop="protocol">
          <el-select v-model="formData.protocol" placeholder="请选择协议" style="width: 100%">
            <el-option label="Modbus TCP" value="MODBUS_TCP" />
            <el-option label="Modbus RTU" value="MODBUS_RTU" />
            <el-option label="MQTT" value="MQTT" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="formData.host" placeholder="请输入主机地址" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="formData.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="从站地址" prop="slaveId">
          <el-input-number v-model="formData.slaveId" :min="1" :max="255" style="width: 100%" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="formData.location" placeholder="请输入位置信息" />
        </el-form-item>
        <el-form-item label="采样间隔" prop="samplingInterval">
          <el-input-number
            v-model="formData.samplingInterval"
            :min="1000"
            :max="3600000"
            :step="1000"
            style="width: 100%"
          />
          <span style="color: #909399; font-size: 12px;">单位：毫秒</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
          />
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
import { deviceApi, type Device, type DeviceQuery } from '@/api'

const tableData = ref<Device[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增设备')
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const deviceTypes = [
  { id: 1, name: '电表', type: 'METER' },
  { id: 2, name: '光伏逆变器', type: 'PV' },
  { id: 3, name: 'BMS', type: 'BMS' },
  { id: 4, name: 'PCS', type: 'PCS' },
  { id: 5, name: '气象站', type: 'WEATHER' }
]

const queryForm = reactive<DeviceQuery>({
  deviceSn: '',
  name: '',
  deviceTypeId: undefined,
  status: undefined,
  page: 1,
  pageSize: 10
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const formData = reactive<Partial<Device>>({
  id: undefined,
  deviceSn: '',
  name: '',
  deviceTypeId: undefined,
  protocol: 'MODBUS_TCP',
  host: '127.0.0.1',
  port: 502,
  slaveId: 1,
  location: '',
  samplingInterval: 5000,
  status: 1,
  remark: ''
})

const formRules: FormRules = {
  deviceSn: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceTypeId: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  protocol: [{ required: true, message: '请选择协议', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  slaveId: [{ required: true, message: '请输入从站地址', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置信息', trigger: 'blur' }],
  samplingInterval: [{ required: true, message: '请输入采样间隔', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const loadData = async () => {
  try {
    const res = await deviceApi.list({
      ...queryForm,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    tableData.value = res.list
    pagination.total = res.total
  } catch (e) {
    ElMessage.error('加载数据失败')
  }
}

const resetQuery = () => {
  queryForm.deviceSn = ''
  queryForm.name = ''
  queryForm.deviceTypeId = undefined
  queryForm.status = undefined
  pagination.page = 1
  loadData()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增设备'
  Object.assign(formData, {
    id: undefined,
    deviceSn: '',
    name: '',
    deviceTypeId: undefined,
    protocol: 'MODBUS_TCP',
    host: '127.0.0.1',
    port: 502,
    slaveId: 1,
    location: '',
    samplingInterval: 5000,
    status: 1,
    remark: ''
  })
  dialogVisible.value = true
}

const handleEdit = (row: Device) => {
  isEdit.value = true
  dialogTitle.value = '编辑设备'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: Device) => {
  ElMessageBox.confirm('确定要删除该设备吗？', '提示', {
    type: 'warning'
  })
    .then(async () => {
      await deviceApi.delete(row.id)
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
          await deviceApi.update(formData as Device)
          ElMessage.success('更新成功')
        } else {
          await deviceApi.create(formData)
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

.query-form {
  margin-bottom: 20px;
}
</style>
