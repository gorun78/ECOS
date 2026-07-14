/**
 * ExpressionEditor — Monaco 单行表达式编辑器
 * 支持 120+ PB 函数自动补全和语法高亮
 * @license Apache-2.0
 */
import React, { useRef, useCallback, useEffect, useMemo } from 'react';
import Editor, { type OnMount, type BeforeMount } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';
import { PB_FUNCTIONS, type PBFunctionDef, CATEGORY_LABELS } from './pbFunctions';
import { FunctionSquare } from 'lucide-react';

// ─── Props ────────────────────────────────────────────

interface ExpressionEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  /** 当前节点可用的列名列表，用于自动补全 */
  availableColumns?: string[];
  /** 是否显示函数提示按钮 */
  showOperatorButton?: boolean;
  onOperatorButtonClick?: () => void;
}

// ─── Monaco integration ──────────────────────────────

// Register PB functions as completion items
function buildCompletionProvider(range: editor.IRange) {
  return {
    provideCompletionItems: (model: editor.ITextModel, position: editor.IPosition) => {
      const word = model.getWordUntilPosition(position);
      const currentRange: editor.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      // Build function completions
      const suggestions: editor.languages.CompletionItem[] = PB_FUNCTIONS.map(
        (fn: PBFunctionDef) => ({
          label: fn.name,
          kind: window.monaco?.languages.CompletionItemKind.Function ?? 1,
          insertText: buildInsertText(fn),
          insertTextRules:
            window.monaco?.languages.CompletionItemInsertTextRule
              .InsertAsSnippet ?? 4,
          detail: CATEGORY_LABELS[fn.category],
          documentation: {
            value: buildFunctionDoc(fn),
          },
          range: currentRange,
          sortText: `0_${fn.name}`, // prioritize
        })
      );

      // Add column name suggestions
      const columnSuggestions: editor.languages.CompletionItem[] = (
        (window as any).__expressionColumns__ || []
      ).map((col: string) => ({
        label: col,
        kind: window.monaco?.languages.CompletionItemKind.Field ?? 5,
        insertText: col,
        detail: 'Column',
        range: currentRange,
        sortText: `1_${col}`,
      }));

      return { suggestions: [...suggestions, ...columnSuggestions] };
    },
  };
}

function buildInsertText(fn: PBFunctionDef): string {
  if (fn.params.length === 0) {
    return `${fn.name}()`;
  }
  const paramsWithPlaceholders = fn.params.map((p, i) => {
    const placeholder = p.defaultValue ? p.defaultValue : `\${${i + 1}:${p.name}}`;
    return placeholder;
  });
  return `${fn.name}(${paramsWithPlaceholders.join(', ')})`;
}

function buildFunctionDoc(fn: PBFunctionDef): string {
  const lines = [
    `### ${fn.name} — ${CATEGORY_LABELS[fn.category]}`,
    '',
    `**签名**: \`${fn.signature}\``,
    '',
    fn.description,
    '',
    '**参数**:',
    ...fn.params.map(
      (p) =>
        `- \`${p.name}\` (${p.type})${p.required ? ' *必填*' : ''}${p.defaultValue ? ` — 默认: ${p.defaultValue}` : ''}`
    ),
    '',
    `**返回**: \`${fn.returnType}\``,
    '',
    `**示例**: \`${fn.example}\``,
  ];
  return lines.join('\n');
}

// Register PB function tokens for syntax highlighting
const PB_FUNCTION_NAMES = PB_FUNCTIONS.map((f) => f.name);

// ─── Component ────────────────────────────────────────

const ExpressionEditor: React.FC<ExpressionEditorProps> = ({
  value,
  onChange,
  placeholder = '输入表达式，如 filter(amount > 100)...',
  className = '',
  disabled = false,
  availableColumns,
  showOperatorButton = false,
  onOperatorButtonClick,
}) => {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  // Store available columns globally for completion provider
  useEffect(() => {
    (window as any).__expressionColumns__ = availableColumns || [];
  }, [availableColumns]);

  const handleBeforeMount: BeforeMount = useCallback((monaco) => {
    // Custom language for PB expressions (extends plaintext/JavaScript)
    monaco.languages.register({ id: 'pb-expression' });

    // Define tokenizer for PB functions
    monaco.languages.setMonarchTokensProvider('pb-expression', {
      defaultToken: '',
      tokenPostfix: '',

      // PB function names
      functions: PB_FUNCTION_NAMES,

      // Operators
      operators: [
        '=', '>', '<', '!', '~', '?', ':',
        '==', '!=', '<=', '>=', '<>', '&&', '||',
        '+=', '-=', '*=', '/=', '%=', '<<=', '>>=',
        '&=', '|=', '^=', '=>',
      ],

      symbols: /[=><!~?:&|+\-*/^%]+/,

      tokens: {
        root: [
          // identifiers & function calls
          [
            /@?[a-zA-Z_][\w$]*/,
            {
              cases: {
                '@functions': 'keyword',
                '@default': 'identifier',
              },
            },
          ],
          // numbers
          [/\d+\.\d*([eE][-+]?\d+)?/, 'number.float'],
          [/\d+/, 'number'],
          // strings
          [/'([^'\\]|\\.)*$/, 'string.invalid'],
          [/'/, 'string', '@stringBody'],
          [/"([^"\\]|\\.)*$/, 'string.invalid'],
          [/"/, 'string', '@stringBody'],
          // operators
          [/[{}[\]()]/, '@brackets'],
          [/@symbols/, { cases: { '@operators': 'operator', '@default': '' } }],
          // whitespace
          [/\s+/, 'white'],
          [/,/, 'delimiter'],
        ],
        stringBody: [
          [/[^\\']+/, 'string'],
          [/\\./, 'string.escape'],
          [/'/, 'string', '@pop'],
        ],
      },
    });

    // Define theme colors
    monaco.editor.defineTheme('pb-expression-theme', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: '6C5CE7', fontStyle: 'bold' }, // PB functions - purple
        { token: 'string', foreground: '00B894' }, // green
        { token: 'number', foreground: 'E17055' }, // orange
        { token: 'operator', foreground: '0984E3' }, // blue
        { token: 'delimiter', foreground: '636E72' },
        { token: 'identifier', foreground: '2D3436' },
        { token: 'comment', foreground: 'B2BEC3', fontStyle: 'italic' },
      ],
      colors: {},
    });

    // Register completion provider
    monaco.languages.registerCompletionItemProvider('pb-expression', {
      provideCompletionItems: (model, position) => {
        return buildCompletionProvider({
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: 1,
          endColumn: position.column,
        }).provideCompletionItems(model, position);
      },
    });

    // Register hover provider for function docs
    monaco.languages.registerHoverProvider('pb-expression', {
      provideHover: (model, position) => {
        const word = model.getWordAtPosition(position);
        if (!word) return null;
        const fn = PB_FUNCTIONS.find((f) => f.name === word.word);
        if (!fn) return null;
        return {
          contents: [{ value: buildFunctionDoc(fn) }],
          range: {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endColumn: word.endColumn,
          },
        };
      },
    });
  }, []);

  const handleMount: OnMount = useCallback((editor, monaco) => {
    editorRef.current = editor;

    // Single-line mode: intercept Enter to prevent new lines
    editor.onKeyDown((e) => {
      if (e.keyCode === monaco.KeyCode.Enter) {
        e.preventDefault();
        e.stopPropagation();
      }
      // Tab for autocomplete (select first suggestion)
      if (e.keyCode === monaco.KeyCode.Tab) {
        e.preventDefault();
        editor.trigger('keyboard', 'editor.action.triggerSuggest', {});
      }
    });
  }, []);

  const handleChange = useCallback(
    (val: string | undefined) => {
      // Strip newlines if somehow inserted
      const cleaned = (val || '').replace(/[\r\n]/g, '');
      onChange(cleaned);
    },
    [onChange]
  );

  // Use hide overflow to prevent scrollbars in single-line mode
  return (
    <div className={`relative group ${className}`}>
      {showOperatorButton && (
        <button
          type="button"
          onClick={onOperatorButtonClick}
          className="absolute left-1 top-1/2 -translate-y-1/2 z-10 p-0.5 rounded hover:bg-purple-100 text-purple-500 transition-colors"
          title="函数库"
          disabled={disabled}
        >
          <FunctionSquare size={14} />
        </button>
      )}
      <div
        className={`border border-slate-200 rounded focus-within:border-blue-400 focus-within:ring-1 focus-within:ring-blue-200 transition-all overflow-hidden ${
          disabled ? 'opacity-50' : ''
        } ${showOperatorButton ? 'pl-7' : ''}`}
        style={{ height: 32 }}
      >
        <Editor
          height="32px"
          language="pb-expression"
          theme="pb-expression-theme"
          value={value}
          onChange={handleChange}
          beforeMount={handleBeforeMount}
          onMount={handleMount}
          options={{
            minimap: { enabled: false },
            lineNumbers: 'off',
            folding: false,
            glyphMargin: false,
            lineDecorationsWidth: 0,
            lineNumbersMinChars: 0,
            scrollBeyondLastLine: false,
            wordWrap: 'off',
            renderLineHighlight: 'none',
            overviewRulerLanes: 0,
            overviewRulerBorder: false,
            hideCursorInOverviewRuler: true,
            scrollbar: {
              vertical: 'hidden',
              horizontal: 'hidden',
              alwaysConsumeMouseWheel: false,
            },
            contextmenu: false,
            quickSuggestions: true,
            suggestOnTriggerCharacters: true,
            tabCompletion: 'on',
            snippetSuggestions: 'inline',
            wordBasedSuggestions: 'off',
            fontSize: 12,
            fontFamily: "'JetBrains Mono', 'Cascadia Code', 'Fira Code', monospace",
            lineHeight: 20,
            padding: { top: 6, bottom: 6 },
            readOnly: disabled,
            domReadOnly: disabled,
            renderWhitespace: 'none',
            guides: { indentation: false },
            stickyScroll: { enabled: false },
          }}
          loading={
            <div className="flex items-center justify-center h-full bg-slate-50 text-[11px] text-slate-400">
              <FunctionSquare size={12} className="mr-1 animate-pulse" />
              Loading...
            </div>
          }
        />
      </div>
    </div>
  );
};

export default ExpressionEditor;
export { PB_FUNCTIONS, type PBFunctionDef };
