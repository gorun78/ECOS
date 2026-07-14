/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Gauge,
  Settings,
  Cpu,
  Database,
  Activity,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  LineChart,
  Terminal,
  Layers,
  FileCheck,
  Server,
  Zap,
  Radio,
  BarChart3,
  Box,
  Send,
  Circle,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import {
  fetchTwinDevices,
  fetchTwinHealth,
  fetchTwinTelemetry,
  fetchTwinDeviceStatus,
  sendTwinCommand,
  TwinDevice,
  TwinTelemetry,
  TwinDeviceStatus,
  TwinHealth,
  TwinCommandResult,
} from "../../../api";
import TelemetryMiniChart from "../TelemetryMiniChart";
import { DEVICE_TYPE_ICON } from "../helpers";

// ── Digital Twin Tab ───────────────────────────────────────
export default function DigitalTwinTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  const [health, setHealth] = useState<TwinHealth | null>(null);
  const [devices, setDevices] = useState<TwinDevice[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null);
  const [telemetryData, setTelemetryData] = useState<TwinTelemetry[]>([]);
  const [deviceStatus, setDeviceStatus] = useState<TwinDeviceStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [telemetryLoading, setTelemetryLoading] = useState(false);

  // Command panel state
  const [command, setCommand] = useState("");
  const [paramsText, setParamsText] = useState("");
  const [sending, setSending] = useState(false);
  const [commandResult, setCommandResult] = useState<TwinCommandResult | null>(null);
  const [commandError, setCommandError] = useState<string | null>(null);

  // Load devices on mount
  const loadDevices = useCallback(async () => {
    setLoading(true);
    try {
      const [h, devs] = await Promise.all([
        fetchTwinHealth(),
        fetchTwinDevices(),
      ]);
      setHealth(h);
      setDevices(devs);
      if (devs.length > 0 && !selectedDeviceId) {
        setSelectedDeviceId(devs[0].deviceId);
      }
    } catch {
      // Graceful
    } finally {
      setLoading(false);
    }
  }, [selectedDeviceId]);

  useEffect(() => {
    loadDevices();
  }, []);

  // Load telemetry and status when device changes
  useEffect(() => {
    if (!selectedDeviceId) return;
    const loadDetails = async () => {
      setTelemetryLoading(true);
      try {
        const [telem, status] = await Promise.all([
          fetchTwinTelemetry(selectedDeviceId, 20),
          fetchTwinDeviceStatus(selectedDeviceId),
        ]);
        setTelemetryData(telem);
        setDeviceStatus(status);
        setCommandResult(null);
        setCommandError(null);
      } catch {
        // Graceful
      } finally {
        setTelemetryLoading(false);
      }
    };
    loadDetails();
  }, [selectedDeviceId]);

  // Send command
  const handleSendCommand = async () => {
    if (!selectedDeviceId || !command.trim()) return;
    setSending(true);
    setCommandError(null);
    setCommandResult(null);
    try {
      let params: Record<string, any> = {};
      if (paramsText.trim()) {
        try {
          params = JSON.parse(paramsText);
        } catch {
          setCommandError("参数JSON格式错误");
          setSending(false);
          return;
        }
      }
      const result = await sendTwinCommand(selectedDeviceId, command.trim(), params);
      setCommandResult(result);
      setCommand("");
      setParamsText("");
      // Refresh device status after command
      const status = await fetchTwinDeviceStatus(selectedDeviceId);
      setDeviceStatus(status);
    } catch (e: any) {
      setCommandError(e.message || "指令发送失败");
    } finally {
      setSending(false);
    }
  };

  const selectedDevice = devices.find(d => d.deviceId === selectedDeviceId);

  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="flex flex-col items-center gap-3">
          <RefreshCw className="w-8 h-8 text-indigo-500 animate-spin" />
          <p className="text-sm text-slate-400">{tl("加载数字孪生数据...", "Loading digital twin data...")}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-grow overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">

        {/* Header with health status */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-bold tracking-tight">{tl("数字孪生设备总控", "Digital Twin Device Control")}</h1>
            <p className={`text-xs ${styles.cardTextMuted} mt-1`}>
              {tl("IoT 设备影子管理、遥测监控与指令下发", "IoT device shadow management, telemetry monitoring & command dispatch")}
            </p>
          </div>
          <div className="flex items-center gap-3">
            {health && (
              <div className="flex items-center gap-2 text-xs">
                <span className={`w-2 h-2 rounded-full ${health.mqtt?.status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'}`}></span>
                <span className="text-slate-500 dark:text-slate-400">MQTT: {health.mqtt?.status}</span>
                <span className="text-slate-300 dark:text-slate-600">|</span>
                <span className="text-slate-500 dark:text-slate-400">{tl("设备数", "Devices")}: {health.device_count}</span>
              </div>
            )}
            <button
              onClick={loadDevices}
              className={`flex items-center gap-1.5 px-3 py-1.5 border border-slate-300 dark:border-slate-600 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer text-xs font-bold rounded-lg transition h-8`}
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>{tl("刷新", "Refresh")}</span>
            </button>
          </div>
        </div>

        {/* Device Cards Grid */}
        <div>
          <h3 className="text-xs font-extrabold uppercase font-mono tracking-wider text-slate-400 mb-3 flex items-center gap-2">
            <Box className="w-3.5 h-3.5" /> {tl("设备状态卡片", "Device Status Cards")}
          </h3>
          {devices.length === 0 ? (
            <div className="border border-dashed border-slate-300 dark:border-slate-700 rounded-xl p-8 text-center text-xs text-slate-400">
              {tl("暂无设备连接", "No devices connected")}
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
              {devices.map((dev) => {
                const isOnline = dev.status === 'online' || dev.status === 'UP';
                const isSelected = dev.deviceId === selectedDeviceId;
                const TypeIcon = DEVICE_TYPE_ICON[dev.type] || DEVICE_TYPE_ICON.default;
                return (
                  <button
                    key={dev.deviceId}
                    onClick={() => setSelectedDeviceId(dev.deviceId)}
                    className={`border rounded-xl p-3 text-left transition-all cursor-pointer ${
                      isSelected
                        ? 'border-indigo-500 bg-indigo-50/50 dark:bg-indigo-900/20 ring-1 ring-indigo-500/30'
                        : `${styles.cardBorder} ${styles.cardBg} hover:border-indigo-300 dark:hover:border-indigo-600`
                    }`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <TypeIcon className={`w-4 h-4 ${isSelected ? 'text-indigo-500' : 'text-slate-400'}`} />
                      <span className={`w-2 h-2 rounded-full ${isOnline ? 'bg-emerald-500 shadow-[0_0_6px_rgba(16,185,129,0.5)]' : 'bg-red-500'}`}></span>
                    </div>
                    <div className="font-bold text-xs truncate">{dev.name}</div>
                    <div className="text-[10px] text-slate-400 mt-0.5 capitalize">{dev.type}</div>
                    {dev.unit && (
                      <div className="text-[10px] text-slate-400 mt-0.5">{dev.unit}</div>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Selected device details */}
        {selectedDevice && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            
            {/* Telemetry chart + list */}
            <div className="lg:col-span-7 space-y-4">
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-3">
                  <LineChart className="w-4 h-4 text-indigo-500 shrink-0" />
                  <h3 className="font-bold text-sm">{tl("实时遥测", "Real-time Telemetry")} — {selectedDevice.name}</h3>
                  {telemetryLoading && <RefreshCw className="w-3 h-3 animate-spin text-slate-400" />}
                </div>
                {telemetryData.length > 0 ? (
                  <div>
                    <TelemetryMiniChart data={telemetryData} color="#818CF8" height={80} />
                    <div className="mt-3 space-y-1 max-h-48 overflow-y-auto">
                      {telemetryData.slice().reverse().map((pt, i) => (
                        <div key={i} className="flex items-center justify-between text-[10px] font-mono py-1 border-b border-slate-100 dark:border-slate-800 last:border-0">
                          <span className="text-slate-400">{pt.ts}</span>
                          <span className="font-bold text-indigo-600 dark:text-indigo-400">{pt.value}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div className="text-[10px] text-slate-400 py-4 text-center">
                    {tl("暂无遥测数据", "No telemetry data available")}
                  </div>
                )}
              </div>
            </div>

            {/* Command Panel + Device Shadow */}
            <div className="lg:col-span-5 space-y-4">
              
              {/* Command Panel */}
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-3">
                  <Terminal className="w-4 h-4 text-indigo-500 shrink-0" />
                  <h3 className="font-bold text-sm">{tl("指令下发", "Command Panel")}</h3>
                </div>
                
                <div className="space-y-3">
                  <div>
                    <label className="text-[10px] font-bold text-slate-400 uppercase block mb-1">
                      {tl("指令", "Command")}
                    </label>
                    <input
                      type="text"
                      value={command}
                      onChange={(e) => setCommand(e.target.value)}
                      placeholder={tl("例如: set_temperature", "e.g. set_temperature")}
                      className={`w-full px-3 py-2 text-xs rounded-lg outline-none font-mono transition ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500`}
                    />
                  </div>
                  <div>
                    <label className="text-[10px] font-bold text-slate-400 uppercase block mb-1">
                      {tl("参数 (JSON)", "Params (JSON)")}
                    </label>
                    <textarea
                      value={paramsText}
                      onChange={(e) => setParamsText(e.target.value)}
                      placeholder='{"value": 25.5}'
                      rows={2}
                      className={`w-full px-3 py-2 text-xs rounded-lg outline-none font-mono transition resize-none ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500`}
                    />
                  </div>
                  <button
                    onClick={handleSendCommand}
                    disabled={sending || !command.trim()}
                    className="w-full flex items-center justify-center gap-1.5 px-3 py-2 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-white text-xs font-bold rounded-lg transition cursor-pointer disabled:cursor-not-allowed"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>{sending ? tl("发送中...", "Sending...") : tl("发送指令", "Send Command")}</span>
                  </button>

                  {/* Command result */}
                  {commandError && (
                    <div className="p-2 bg-red-500/10 border border-red-500/20 rounded text-[10px] text-red-600 dark:text-red-400 font-mono">
                      {commandError}
                    </div>
                  )}
                  {commandResult && (
                    <div className="p-2 bg-emerald-500/10 border border-emerald-500/20 rounded text-[10px] font-mono">
                      <div className="text-emerald-600 dark:text-emerald-400 font-bold mb-1">
                        {tl("指令已发送", "Command Sent")}: {commandResult.command}
                      </div>
                      <div className="text-slate-500 dark:text-slate-400">
                        {tl("状态", "Status")}: {commandResult.status}
                      </div>
                      {Object.keys(commandResult.params).length > 0 && (
                        <div className="text-slate-400 mt-0.5 break-all">
                          Params: {JSON.stringify(commandResult.params)}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>

              {/* Device Shadow */}
              {deviceStatus && (
                <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-2xs`}>
                  <div className="flex items-center gap-2 mb-3">
                    <Layers className="w-4 h-4 text-indigo-500 shrink-0" />
                    <h3 className="font-bold text-sm">{tl("设备影子", "Device Shadow")}</h3>
                    <span className="text-[10px] text-slate-400 ml-auto">
                      {tl("遥测计数", "Telemetry count")}: {deviceStatus.telemetryCount}
                    </span>
                  </div>

                  <div className="space-y-3">
                    {/* Reported */}
                    <div>
                      <div className="text-[10px] font-bold text-slate-400 uppercase mb-1.5 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                        {tl("上报状态", "Reported")}
                      </div>
                      <div className="p-2 bg-black/5 dark:bg-white/5 rounded text-[10px] font-mono max-h-32 overflow-y-auto">
                        {Object.keys(deviceStatus.shadow.reported).length > 0 ? (
                          <pre className="whitespace-pre-wrap break-all text-slate-600 dark:text-slate-300">
                            {JSON.stringify(deviceStatus.shadow.reported, null, 2)}
                          </pre>
                        ) : (
                          <span className="text-slate-400">{tl("暂无上报数据", "No reported data")}</span>
                        )}
                      </div>
                    </div>

                    {/* Desired */}
                    <div>
                      <div className="text-[10px] font-bold text-slate-400 uppercase mb-1.5 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
                        {tl("期望状态", "Desired")}
                      </div>
                      <div className="p-2 bg-black/5 dark:bg-white/5 rounded text-[10px] font-mono max-h-32 overflow-y-auto">
                        {Object.keys(deviceStatus.shadow.desired).length > 0 ? (
                          <pre className="whitespace-pre-wrap break-all text-slate-600 dark:text-slate-300">
                            {JSON.stringify(deviceStatus.shadow.desired, null, 2)}
                          </pre>
                        ) : (
                          <span className="text-slate-400">{tl("暂无期望配置", "No desired config")}</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* No device selected prompt */}
        {!selectedDevice && devices.length > 0 && (
          <div className="border border-dashed border-slate-300 dark:border-slate-700 rounded-xl p-8 text-center text-xs text-slate-400">
            {tl("请点击上方设备卡片查看详情", "Click a device card above to view details")}
          </div>
        )}

      </div>
    </div>
  );
}
