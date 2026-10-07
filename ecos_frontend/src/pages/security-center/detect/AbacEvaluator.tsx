import { useState } from 'react';
import { Loader2, AlertTriangle, CheckCircle, XCircle } from 'lucide-react';
import { evaluateAbacPolicy } from '../../../api';
import { inputClasses, btnPrimary } from './helpers';

// ── ABAC Policy Evaluator ────────────────────────────────────
export default function AbacEvaluator({ t, locale, styles }: { t: (k: string) => string; locale: string; styles: any }) {
  const [userId, setUserId] = useState('');
  const [roles, setRoles] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [resourceId, setResourceId] = useState('');
  const [action, setAction] = useState('read');
  const [evaluating, setEvaluating] = useState(false);
  const [result, setResult] = useState<{ allowed: boolean; message?: string; details?: any } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleEvaluate = async () => {
    if (!userId || !resourceType || !resourceId) return;
    setEvaluating(true);
    setError(null);
    setResult(null);
    try {
      const roleList = roles.split(',').map(r => r.trim()).filter(Boolean);
      const res = await evaluateAbacPolicy({
        subject: { userId, roles: roleList },
        resource: { type: resourceType, id: resourceId },
        action,
      });
      setResult(res);
    } catch (e: any) {
      setError(e.message || 'Evaluation failed');
    } finally {
      setEvaluating(false);
    }
  };

  const actions = ['read', 'write', 'delete', 'execute'];

  return (
    <div className="p-6 space-y-6 overflow-auto h-full">
      <div>
        <h3 className={`text-lg font-bold mb-1 ${styles.cardText}`}>{t('sec.abac.evaluate')}</h3>
        <p className={`text-sm ${styles.muted}`}>{t('sec.abac.evaluate.desc')}</p>
      </div>

      {/* Subject section */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <h4 className={`text-sm font-semibold mb-3 ${styles.cardText}`}>{t('sec.abac.subject')}</h4>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.userId')}</label>
            <input
              type="text"
              value={userId}
              onChange={e => setUserId(e.target.value)}
              placeholder="user_001"
              className={inputClasses(styles)}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.roles')}</label>
            <input
              type="text"
              value={roles}
              onChange={e => setRoles(e.target.value)}
              placeholder="admin, analyst"
              className={inputClasses(styles)}
            />
          </div>
        </div>
      </div>

      {/* Resource section */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <h4 className={`text-sm font-semibold mb-3 ${styles.cardText}`}>{t('sec.abac.resource')}</h4>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.resourceType')}</label>
            <input
              type="text"
              value={resourceType}
              onChange={e => setResourceType(e.target.value)}
              placeholder="table"
              className={inputClasses(styles)}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1.5 ${styles.muted}`}>{t('sec.abac.resourceId')}</label>
            <input
              type="text"
              value={resourceId}
              onChange={e => setResourceId(e.target.value)}
              placeholder="customer_data"
              className={inputClasses(styles)}
            />
          </div>
        </div>
      </div>

      {/* Action selector */}
      <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder}`}>
        <h4 className={`text-sm font-semibold mb-3 ${styles.cardText}`}>{t('sec.abac.action')}</h4>
        <div className="flex gap-2">
          {actions.map(a => (
            <button
              key={a}
              onClick={() => setAction(a)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer ${
                action === a
                  ? `${styles.accentBg} text-white`
                  : `${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} hover:${styles.sidebarHoverBg}`
              }`}
            >
              {t(`sec.abac.action.${a}`)}
            </button>
          ))}
        </div>
      </div>

      {/* Evaluate button */}
      <button
        onClick={handleEvaluate}
        disabled={evaluating || !userId || !resourceType || !resourceId}
        className={btnPrimary(styles)}
      >
        {evaluating ? (
          <span className="flex items-center gap-2"><Loader2 size={14} className="animate-spin" />{t('sec.abac.evaluating')}</span>
        ) : (
          t('sec.abac.evalBtn')
        )}
      </button>

      {/* Error */}
      {error && (
        <div className={`p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm dark:bg-red-950/30 dark:border-red-800 dark:text-red-400 flex items-start gap-2`}>
          <AlertTriangle size={16} className="shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      {/* Result */}
      {result && (
        <div className={`p-5 rounded-xl ${styles.cardBg} border ${styles.cardBorder} space-y-4`}>
          <h4 className={`text-sm font-semibold ${styles.cardText}`}>{t('sec.abac.result')}</h4>
          <div className="flex items-center gap-3">
            {result.allowed ? (
              <CheckCircle size={24} className="text-green-500" />
            ) : (
              <XCircle size={24} className="text-red-500" />
            )}
            <span className={`text-lg font-bold ${result.allowed ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
              {result.allowed ? t('sec.abac.allow') : t('sec.abac.deny')}
            </span>
          </div>
          {result.message && (
            <p className={`text-sm ${styles.muted}`}>{result.message}</p>
          )}
          {result.details && (
            <div>
              <p className={`text-xs font-medium mb-2 ${styles.muted}`}>{t('sec.abac.details')}</p>
              <pre className={`p-3 rounded-lg text-xs font-mono overflow-auto max-h-40 ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder}`}>
                {JSON.stringify(result.details, null, 2)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
