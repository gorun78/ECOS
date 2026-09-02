/**
 * AI Workbench — Radar Chart Canvas sub-component
 * Capability radar for the Agent Evaluation panel. Pulls label strings
 * from `dashboard.radar.*` namespace so the canvas labels also respect locale.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useRef, useEffect } from 'react';
import { Target, Brain, Eye, Shield, Gauge, Sparkles } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';

interface RadarChartProps {
  scores: Record<string, number>;
}

/**
 * Custom hook: returns the 6 capability dimensions with i18n keys
 * and primitive-component icons. Icon type is React.ComponentType.
 */
function useRadarDims() {
  const { t } = useLanguage();
  return [
    { key: 'accuracy', label: t('dashboard.radar.accuracy'), color: '#3b82f6', icon: Target },
    { key: 'relevance', label: t('dashboard.radar.relevance'), color: '#8b5cf6', icon: Brain },
    { key: 'completeness', label: t('dashboard.radar.completeness'), color: '#10b981', icon: Eye },
    { key: 'safety', label: t('dashboard.radar.safety'), color: '#ef4444', icon: Shield },
    { key: 'efficiency', label: t('dashboard.radar.efficiency'), color: '#f59e0b', icon: Gauge },
    { key: 'creativity', label: t('dashboard.radar.creativity'), color: '#ec4899', icon: Sparkles },
  ];
}

export default function RadarChart({ scores }: RadarChartProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const size = 280;
  const center = size / 2;
  const radius = 110;
  const levels = 5;
  const dims = useRadarDims();
  const themingLabel = useLanguage();

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    canvas.style.width = `${size}px`;
    canvas.style.height = `${size}px`;
    ctx.scale(dpr, dpr);

    ctx.clearRect(0, 0, size, size);

    const count = dims.length;
    const step = (Math.PI * 2) / count;

    // Draw concentric polygons
    for (let level = 1; level <= levels; level++) {
      ctx.beginPath();
      for (let i = 0; i < count; i++) {
        const angle = step * i - Math.PI / 2;
        const r = (radius / levels) * level;
        const x = center + r * Math.cos(angle);
        const y = center + r * Math.sin(angle);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.closePath();
      ctx.strokeStyle = 'rgba(148, 163, 184, 0.2)';
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // Draw axes
    for (let i = 0; i < count; i++) {
      const angle = step * i - Math.PI / 2;
      ctx.beginPath();
      ctx.moveTo(center, center);
      ctx.lineTo(center + radius * Math.cos(angle), center + radius * Math.sin(angle));
      ctx.strokeStyle = 'rgba(148, 163, 184, 0.25)';
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // Draw data polygon
    ctx.beginPath();
    for (let i = 0; i < count; i++) {
      const dim = dims[i];
      const val = scores[dim.key] || 0;
      const r = (val / 100) * radius;
      const angle = step * i - Math.PI / 2;
      const x = center + r * Math.cos(angle);
      const y = center + r * Math.sin(angle);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.fillStyle = 'rgba(59, 130, 246, 0.15)';
    ctx.fill();
    ctx.strokeStyle = '#3b82f6';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Draw data points
    for (let i = 0; i < count; i++) {
      const dim = dims[i];
      const val = scores[dim.key] || 0;
      const r = (val / 100) * radius;
      const angle = step * i - Math.PI / 2;
      const x = center + r * Math.cos(angle);
      const y = center + r * Math.sin(angle);
      ctx.beginPath();
      ctx.arc(x, y, 4, 0, Math.PI * 2);
      ctx.fillStyle = dim.color;
      ctx.fill();
    }

    // Draw labels (i18n via t included through dims)
    ctx.font = 'bold 11px Inter, system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (let i = 0; i < count; i++) {
      const angle = step * i - Math.PI / 2;
      const labelR = radius + 22;
      const x = center + labelR * Math.cos(angle);
      const y = center + labelR * Math.sin(angle);
      ctx.fillStyle = '#94a3b8';
      ctx.fillText(dims[i].label, x, y);
    }

    // Center score (average)
    const avg = Math.round(
      dims.reduce((sum, d) => sum + (scores[d.key] || 0), 0) / count,
    );
    ctx.font = 'bold 28px Inter, system-ui, sans-serif';
    ctx.fillStyle = '#e2e8f0';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`${avg}`, center, center - 6);
    ctx.font = '10px Inter, system-ui, sans-serif';
    ctx.fillStyle = '#64748b';
    ctx.fillText(themingLabel.t('dashboard.radar.totalScore'), center, center + 18);
  }, [scores, size, dims, themingLabel]);

  return (
    <div className="flex flex-col items-center">
      <canvas ref={canvasRef} className="rounded-lg" />
    </div>
  );
}
