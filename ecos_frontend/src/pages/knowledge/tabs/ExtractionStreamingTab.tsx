// 知识萃取 Tab（Wave 0）
// 结构化→图谱 / 非结构化→向量库 双通道（通道可手选，结构化优先）。
// 候选三元组审核入口：跳转提示至「知识融合」（独立 Tab，不嵌套导航）。
// TODO Wave1/2: 后端萃取触发端点（kb-engine extract）与会话式抽取进度接入。
import { useState } from 'react';
import { ListChecks, Layers, Network, Radio, Workflow } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';

/** 通道选择枚举：结构化 / 非结构化 / 双通道（结构化优先） */
type ChannelChoice = 'structured' | 'unstructured' | 'both';

const CHANNEL_OPTIONS: Array<{ value: ChannelChoice; i18nKey: string; icon: 'Layers' | 'Network' | 'Workflow'; descKey: string }> = [
  { value: 'structured', i18nKey: 'knowledge.streaming.channel.structured', icon: 'Network', descKey: 'knowledge.datasync.channel_structured' },
  { value: 'unstructured', i18nKey: 'knowledge.streaming.channel.unstructured', icon: 'Layers', descKey: 'knowledge.datasync.channel_unstructured' },
  { value: 'both', i18nKey: 'knowledge.streaming.channel.both', icon: 'Workflow', descKey: 'knowledge.streaming.priority' },
];

export default function ExtractionStreamingTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [channel, setChannel] = useState<ChannelChoice>('both');

  return (
    <div className="h-full overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* 标题 */}
        <div className="space-y-1">
          <h2 className="font-bold text-xl tracking-tight flex items-center gap-2" style={{ color: styles.cardText }}>
            <Workflow className="w-5 h-5" style={{ color: styles.accentText }} />
            {t('knowledge.streaming.title')}
          </h2>
          <p className="text-xs" style={{ color: styles.cardTextMuted }}>{t('knowledge.streaming.subtitle')}</p>
          <p className="font-mono text-[10px] tracking-wider uppercase mt-2" style={{ color: styles.cardTextMuted }}>
            {t('knowledge.streaming.priority')}
          </p>
        </div>

        {/* 通道选择（radio，抽象公共结构避免重复逻辑） */}
        <div className={`border rounded-xl p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <h3 className="font-semibold text-sm" style={{ color: styles.cardText }}>
            {t('knowledge.streaming.channel_select')}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {CHANNEL_OPTIONS.map(opt => {
              const Icon = opt.icon === 'Network' ? Network : opt.icon === 'Layers' ? Layers : Workflow;
              const active = channel === opt.value;
              return (
                <button
                  key={opt.value}
                  onClick={() => setChannel(opt.value)}
                  className={`p-3 rounded-lg border transition hover:opacity-80 text-left ${
                    active ? 'font-semibold' : 'opacity-80'
                  }`}
                  style={{
                    borderColor: active ? styles.accentBg : styles.cardBorder,
                    background: active ? styles.badgeBg : styles.inputBg,
                  }}
                >
                  <div className="flex items-center gap-2 text-xs" style={{ color: styles.cardText }}>
                    <Icon className="w-4 h-4" style={{ color: active ? styles.accentText : styles.cardTextMuted }} />
                    {t(opt.i18nKey)}
                  </div>
                  <div className="mt-1.5 text-[10px] font-mono tracking-wider uppercase" style={{ color: styles.cardTextMuted }}>
                    {t(opt.descKey)}
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* 发起萃取（Stub：Wave1/2 后端端点上线前禁用） */}
        <div className={`border rounded-xl p-4 flex items-center justify-between flex-wrap gap-3 ${styles.cardBg} ${styles.cardBorder}`}>
          {/* TODO Wave1/2: 接通 kb-engine 萃取触发端点（结构化走 /extract/structured，非结构化走文档解析管道） */}
          <button
            disabled
            title={t('knowledge.streaming.run_stub_hint')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold opacity-50 cursor-not-allowed transition"
            style={{ background: styles.accentBg, color: '#fff' }}
          >
            <Radio className="w-3.5 h-3.5" />
            {t('knowledge.streaming.run_extract')}
          </button>
          <p className="text-[10px] font-mono tracking-wider uppercase" style={{ color: styles.cardTextMuted }}>
            {t('knowledge.streaming.run_stub_hint')}
          </p>
        </div>

        {/* 候选三元组审核入口（跳转提示，不嵌套 review 组件） */}
        <div className={`border rounded-xl p-4 flex items-center justify-between flex-wrap gap-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center gap-2">
            <ListChecks className="w-4 h-4" style={{ color: styles.accentText }} />
            <div>
              <div className="text-xs font-semibold" style={{ color: styles.cardText }}>
                {t('knowledge.streaming.review_entry')}
              </div>
              <div className="text-[10px]" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.streaming.review_entry_hint')}
              </div>
            </div>
          </div>
          {/* 知识融合（review）为独立 Tab，左侧导航直达，避免嵌套导航 */}
          <span className={`text-[10px] font-mono tracking-wider uppercase ${styles.accentText}`}>
            {t('knowledge.nav.review')}
          </span>
        </div>
      </div>
    </div>
  );
}
