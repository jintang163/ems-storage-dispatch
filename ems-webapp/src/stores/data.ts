import { defineStore } from 'pinia'
import { ref, onUnmounted } from 'vue'
import type { RealtimeDataVO } from '@/api'
import { dataApi } from '@/api'
import mqtt from 'mqtt'

export const useDataStore = defineStore('data', () => {
  const meterData = ref<RealtimeDataVO | null>(null)
  const pvData = ref<RealtimeDataVO | null>(null)
  const bmsData = ref<RealtimeDataVO | null>(null)
  const pcsData = ref<RealtimeDataVO | null>(null)
  const mqttConnected = ref(false)

  let mqttClient: mqtt.MqttClient | null = null
  let pollInterval: number | null = null

  const deviceSns = {
    meter: 'METER-001',
    pv: 'PV-001',
    bms: 'BMS-001',
    pcs: 'PCS-001'
  }

  const fetchRealtimeData = async () => {
    try {
      const [meter, pv, bms, pcs] = await Promise.all([
        dataApi.getRealtime('meter', deviceSns.meter).catch(() => null),
        dataApi.getRealtime('pv', deviceSns.pv).catch(() => null),
        dataApi.getRealtime('bms', deviceSns.bms).catch(() => null),
        dataApi.getRealtime('pcs', deviceSns.pcs).catch(() => null)
      ])

      if (meter) meterData.value = meter
      if (pv) pvData.value = pv
      if (bms) bmsData.value = bms
      if (pcs) pcsData.value = pcs
    } catch (e) {
      console.error('Failed to fetch realtime data:', e)
    }
  }

  const startPolling = (interval: number = 5000) => {
    if (pollInterval) return
    fetchRealtimeData()
    pollInterval = window.setInterval(fetchRealtimeData, interval)
  }

  const stopPolling = () => {
    if (pollInterval) {
      clearInterval(pollInterval)
      pollInterval = null
    }
  }

  const connectMqtt = (brokerUrl: string = 'ws://localhost:8083/mqtt') => {
    try {
      mqttClient = mqtt.connect(brokerUrl, {
        clientId: `ems-web-${Date.now()}`,
        clean: true,
        reconnectPeriod: 5000
      })

      mqttClient.on('connect', () => {
        mqttConnected.value = true
        console.log('MQTT connected')

        const topics = [
          `ems/device/meter/${deviceSns.meter}/data`,
          `ems/device/pv/${deviceSns.pv}/data`,
          `ems/device/bms/${deviceSns.bms}/data`,
          `ems/device/pcs/${deviceSns.pcs}/data`
        ]

        topics.forEach((topic) => {
          mqttClient?.subscribe(topic, (err) => {
            if (err) console.error(`Subscribe failed: ${topic}`, err)
          })
        })
      })

      mqttClient.on('message', (topic, message) => {
        try {
          const payload = JSON.parse(message.toString())
          if (topic.includes('/meter/')) {
            meterData.value = {
              deviceSn: payload.deviceSn,
              deviceType: 'meter',
              data: payload,
              timestamp: payload.timestamp || Date.now()
            }
          } else if (topic.includes('/pv/')) {
            pvData.value = {
              deviceSn: payload.deviceSn,
              deviceType: 'pv',
              data: payload,
              timestamp: payload.timestamp || Date.now()
            }
          } else if (topic.includes('/bms/')) {
            bmsData.value = {
              deviceSn: payload.deviceSn,
              deviceType: 'bms',
              data: payload,
              timestamp: payload.timestamp || Date.now()
            }
          } else if (topic.includes('/pcs/')) {
            pcsData.value = {
              deviceSn: payload.deviceSn,
              deviceType: 'pcs',
              data: payload,
              timestamp: payload.timestamp || Date.now()
            }
          }
        } catch (e) {
          console.error('Failed to parse MQTT message:', e)
        }
      })

      mqttClient.on('error', (err) => {
        console.error('MQTT error:', err)
      })

      mqttClient.on('close', () => {
        mqttConnected.value = false
        console.log('MQTT disconnected')
      })

      mqttClient.on('reconnect', () => {
        console.log('MQTT reconnecting...')
      })
    } catch (e) {
      console.error('Failed to connect MQTT:', e)
    }
  }

  const disconnectMqtt = () => {
    if (mqttClient) {
      mqttClient.end()
      mqttClient = null
      mqttConnected.value = false
    }
  }

  onUnmounted(() => {
    stopPolling()
    disconnectMqtt()
  })

  return {
    meterData,
    pvData,
    bmsData,
    pcsData,
    mqttConnected,
    deviceSns,
    fetchRealtimeData,
    startPolling,
    stopPolling,
    connectMqtt,
    disconnectMqtt
  }
})
