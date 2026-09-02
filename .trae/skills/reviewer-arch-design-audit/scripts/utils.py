"""
工具函数模块

提供审计过程中通用的辅助函数
"""

def to_snake_case(name):
    """
    将驼峰命名转换为蛇形命名
    
    Args:
        name (str): 原始名称
        
    Returns:
        str: 蛇形命名名称
    """
    import re
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()


def is_snake_case(name):
    """
    检查名称是否符合蛇形命名规范
    
    Args:
        name (str): 要检查的名称
        
    Returns:
        bool: 是否为蛇形命名
    """
    import re
    return bool(re.match(r'^[a-z][a-z0-9_]*$', name))


def is_reserved_word(name):
    """
    检查名称是否为数据库保留字
    
    Args:
        name (str): 要检查的名称
        
    Returns:
        bool: 是否为保留字
    """
    reserved_words = {
        'select', 'insert', 'update', 'delete', 'from', 'where', 'and', 'or',
        'not', 'in', 'like', 'between', 'as', 'order', 'by', 'group', 'having',
        'limit', 'offset', 'join', 'left', 'right', 'inner', 'outer', 'on',
        'union', 'all', 'distinct', 'count', 'sum', 'avg', 'min', 'max',
        'create', 'drop', 'alter', 'table', 'column', 'index', 'primary',
        'foreign', 'key', 'references', 'constraint', 'default', 'null',
        'not null', 'unique', 'check', 'auto_increment', 'identity',
        'true', 'false', 'null', 'default', 'current', 'timestamp', 'date',
        'time', 'interval', 'case', 'when', 'then', 'else', 'end',
        'if', 'exists', 'cast', 'convert', 'function', 'procedure', 'trigger',
        'view', 'transaction', 'commit', 'rollback', 'savepoint',
        'user', 'password', 'role', 'grant', 'revoke', 'database', 'schema',
        'sequence', 'cursor', 'exception', 'raise', 'declare', 'begin',
        'loop', 'while', 'for', 'repeat', 'until', 'exit', 'return',
        'and', 'or', 'not', 'between', 'like', 'in', 'is', 'as', 'on', 'at',
        'of', 'to', 'with', 'without', 'over', 'partition', 'row', 'rows',
        'range', 'preceding', 'following', 'current row', 'unbounded',
        'asc', 'desc', 'nulls', 'first', 'last', 'collate', 'natural',
        'cross', 'full', 'natural join', 'using', 'straight_join',
        'force index', 'ignore index', 'use index', 'explain', 'analyze',
        'lock', 'flush', 'optimize', 'repair', 'check', 'backup', 'restore',
        'shutdown', 'start', 'stop', 'status', 'variables', 'processlist',
        'kill', 'connect', 'disconnect', 'version', 'charset', 'collation',
        'engine', 'tablespace', 'logfile', 'binlog', 'relaylog', 'innodb',
        'myisam', 'memory', 'merge', 'archive', 'csv', 'blackhole', 'federated',
        'ndbcluster', 'ndb', 'cluster', 'partition', 'subpartition',
        'tables', 'columns', 'indexes', 'constraints', 'triggers', 'views',
        'routines', 'events', 'functions', 'procedures', 'databases', 'schemas',
        'catalogs', 'collations', 'charactersets', 'plugins', 'engines',
        'logs', 'master', 'slave', 'replication', 'binlog', 'relaylog',
        'gtid', 'transaction', 'xa', 'prepare', 'rollback', 'commit',
        'savepoint', 'lock', 'unlock', 'flush', 'reset', 'change', 'master',
        'slave', 'start', 'stop', 'replication', 'heartbeat', 'connection',
        'purge', 'binary', 'logs', 'expire', 'logs', 'days', 'show', 'binary',
        'logs', 'events', 'status', 'variables', 'processlist', 'grants',
        'privileges', 'databases', 'tables', 'columns', 'indexes', 'triggers',
        'views', 'routines', 'functions', 'procedures', 'events', 'logs',
        'errors', 'warnings', 'profile', 'profiles', 'relaylog', 'events',
        'innodb', 'status', 'innodb', 'locks', 'engine', 'status',
        'variables', 'global', 'session', 'system', 'status', 'global',
        'session', 'process', 'list', 'hostnames', 'plugin', 'innodb',
        'buffer', 'pool', 'size', 'innodb', 'log', 'file', 'size',
        'innodb', 'flush', 'log', 'at', 'trx', 'commit', 'innodb', 'flush',
        'log', 'wait', 'timeout', 'innodb', 'lock', 'wait', 'timeout',
        'innodb', 'deadlock', 'detect', 'long', 'wait', 'timeout',
        'innodb', 'read', 'only', 'innodb', 'undo', 'tablespaces',
        'max', 'allowed', 'packet', 'size', 'query', 'cache', 'type',
        'query', 'cache', 'size', 'query', 'cache', 'limit', 'query',
        'cache', 'min', 'res', 'unit', 'query', 'cache', 'wlock', 'invalidate',
        'table', 'open', 'cache', 'table', 'definition', 'cache',
        'tmp', 'table', 'size', 'max', 'heap', 'table', 'size',
        'max', 'connections', 'max', 'user', 'connections',
        'connect', 'timeout', 'wait', 'timeout', 'interactive', 'timeout',
        'lock', 'wait', 'timeout', 'lock', 'wait', 'exponent',
        'innodb', 'lock', 'wait', 'timeout', 'innodb', 'deadlock', 'detect',
        'long', 'wait', 'timeout', 'innodb', 'read', 'only',
        'innodb', 'undo', 'tablespaces', 'max', 'allowed', 'packet', 'size',
        'query', 'cache', 'type', 'query', 'cache', 'size',
        'query', 'cache', 'limit', 'query', 'cache', 'min', 'res', 'unit',
        'query', 'cache', 'wlock', 'invalidate', 'table', 'open', 'cache',
        'table', 'definition', 'cache', 'tmp', 'table', 'size',
        'max', 'heap', 'table', 'size', 'max', 'connections',
        'max', 'user', 'connections', 'connect', 'timeout', 'wait', 'timeout',
        'interactive', 'timeout', 'lock', 'wait', 'timeout',
        'lock', 'wait', 'exponent', 'default', 'storage', 'engine',
        'default', 'tmp', 'storage', 'engine', 'max', 'execution', 'time',
        'max', 'execution', 'time', 'for', 'write', 'operations',
        'max', 'execution', 'time', 'for', 'read', 'operations',
        'optimizer', 'trace', 'optimizer', 'trace', 'max', 'mem', 'size',
        'optimizer', 'trace', 'offset', 'optimizer', 'trace', 'enabled',
        'optimizer', 'trace', 'enabled', 'for', 'queries', 'cost',
        'optimizer', 'trace', 'enabled', 'for', 'queries', 'cost', 'threshold',
        'optimizer', 'trace', 'max', 'mem', 'size', 'optimizer', 'trace',
        'offset', 'optimizer', 'trace', 'enabled', 'optimizer', 'trace',
        'enabled', 'for', 'queries', 'cost', 'optimizer', 'trace',
        'enabled', 'for', 'queries', 'cost', 'threshold',
        'log', 'error', 'log', 'error', 'verbosity', 'log', 'error', 'rate',
        'limit', 'log', 'error', 'rate', 'limit', 'burst', 'log', 'error',
        'log', 'warnings', 'log', 'errors', 'log', 'warnings', 'log', 'errors',
        'general', 'log', 'general', 'log', 'file', 'general', 'log', 'slow',
        'slow', 'query', 'log', 'slow', 'query', 'log', 'file',
        'long', 'query', 'time', 'slow', 'query', 'log', 'always',
        'slow', 'query', 'log', 'min', 'examined', 'row', 'limit',
        'log', 'queries', 'not', 'using', 'indexes', 'log', 'throttle', 'queries',
        'not', 'using', 'indexes', 'log', 'raw', 'slow', 'query', 'log',
        'log', 'output', 'log', 'output', 'slow', 'query', 'log', 'output',
        'expire', 'logs', 'days', 'max', 'binlog', 'size', 'sync', 'binlog',
        'binlog', 'cache', 'size', 'max', 'binlog', 'cache', 'size',
        'binlog', 'stmt', 'cache', 'size', 'max', 'binlog', 'stmt', 'cache', 'size',
        'binlog', 'format', 'binlog', 'row', 'image', 'binlog', 'row', 'event',
        'max', 'size', 'binlog', 'transaction', 'compress', 'binlog', 'checksum',
        'binlog', 'rows', 'query', 'log', 'events', 'binlog', 'rows', 'query',
        'log', 'events', 'minimal', 'binlog', 'rows', 'query', 'log', 'events',
        'verbose', 'binlog', 'rows', 'query', 'log', 'events', 'include',
        'binlog', 'rows', 'query', 'log', 'events', 'exclude',
        'binlog', 'direct_non_transactional_updates', 'binlog', 'error',
        'action', 'binlog', 'group_commit_sync_delay',
        'binlog', 'group_commit_sync_no_delay_count',
        'log', 'slave', 'updates', 'log', 'slave', 'updates', 'to', 'table',
        'log', 'slave', 'updates', 'to', 'relay', 'log',
        'relay', 'log', 'relay', 'log', 'index', 'relay', 'log', 'info', 'file',
        'relay', 'log', 'recover', 'relay', 'log', 'purge', 'threshold',
        'relay', 'log', 'space', 'limit', 'sync', 'relay', 'log',
        'master', 'info', 'file', 'relay', 'log', 'info', 'file',
        'slave', 'load', 'tmp', 'dir', 'slave', 'exec', 'mode',
        'slave', 'parallel', 'type', 'slave', 'parallel', 'workers',
        'slave', 'preserve', 'commit', 'order', 'slave', 'checkpoint', 'period',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'group', 'max',
        'slave', 'checkpoint', 'algorithm', 'slave', 'checkpoint', 'algorithm',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'algorithm',
        'slave', 'parallel', 'type', 'slave', 'parallel', 'workers',
        'slave', 'preserve', 'commit', 'order', 'slave', 'checkpoint', 'period',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'algorithm',
        'transaction', 'isolation', 'level', 'innodb', 'read', 'committed',
        'innodb', 'repeatable', 'read', 'innodb', 'serializable', 'innodb',
        'autocommit', 'innodb', 'flush', 'log', 'at', 'trx', 'commit',
        'innodb', 'flush', 'log', 'wait', 'timeout', 'innodb', 'lock', 'wait',
        'timeout', 'innodb', 'deadlock', 'detect', 'long', 'wait', 'timeout',
        'innodb', 'read', 'only', 'innodb', 'undo', 'tablespaces', 'max',
        'allowed', 'packet', 'size', 'query', 'cache', 'type', 'query', 'cache',
        'size', 'query', 'cache', 'limit', 'query', 'cache', 'min', 'res', 'unit',
        'query', 'cache', 'wlock', 'invalidate', 'table', 'open', 'cache',
        'table', 'definition', 'cache', 'tmp', 'table', 'size',
        'max', 'heap', 'table', 'size', 'max', 'connections', 'max', 'user',
        'connections', 'connect', 'timeout', 'wait', 'timeout', 'interactive',
        'timeout', 'lock', 'wait', 'timeout', 'lock', 'wait', 'exponent',
        'default', 'storage', 'engine', 'default', 'tmp', 'storage', 'engine',
        'max', 'execution', 'time', 'max', 'execution', 'time', 'for', 'write',
        'operations', 'max', 'execution', 'time', 'for', 'read', 'operations',
        'optimizer', 'trace', 'optimizer', 'trace', 'max', 'mem', 'size',
        'optimizer', 'trace', 'offset', 'optimizer', 'trace', 'enabled',
        'optimizer', 'trace', 'enabled', 'for', 'queries', 'cost',
        'optimizer', 'trace', 'enabled', 'for', 'queries', 'cost', 'threshold',
        'log', 'error', 'log', 'error', 'verbosity', 'log', 'error', 'rate',
        'limit', 'log', 'error', 'rate', 'limit', 'burst', 'log', 'error',
        'log', 'warnings', 'log', 'errors', 'log', 'warnings', 'log', 'errors',
        'general', 'log', 'general', 'log', 'file', 'general', 'log', 'slow',
        'slow', 'query', 'log', 'slow', 'query', 'log', 'file',
        'long', 'query', 'time', 'slow', 'query', 'log', 'always',
        'slow', 'query', 'log', 'min', 'examined', 'row', 'limit',
        'log', 'queries', 'not', 'using', 'indexes', 'log', 'throttle', 'queries',
        'not', 'using', 'indexes', 'log', 'raw', 'slow', 'query', 'log',
        'log', 'output', 'log', 'output', 'slow', 'query', 'log', 'output',
        'expire', 'logs', 'days', 'max', 'binlog', 'size', 'sync', 'binlog',
        'binlog', 'cache', 'size', 'max', 'binlog', 'cache', 'size',
        'binlog', 'stmt', 'cache', 'size', 'max', 'binlog', 'stmt', 'cache', 'size',
        'binlog', 'format', 'binlog', 'row', 'image', 'binlog', 'row', 'event',
        'max', 'size', 'binlog', 'transaction', 'compress', 'binlog', 'checksum',
        'binlog', 'rows', 'query', 'log', 'events', 'binlog', 'rows', 'query',
        'log', 'events', 'minimal', 'binlog', 'rows', 'query', 'log', 'events',
        'verbose', 'binlog', 'rows', 'query', 'log', 'events', 'include',
        'binlog', 'rows', 'query', 'log', 'events', 'exclude',
        'binlog', 'direct_non_transactional_updates', 'binlog', 'error',
        'action', 'binlog', 'group_commit_sync_delay',
        'binlog', 'group_commit_sync_no_delay_count',
        'log', 'slave', 'updates', 'log', 'slave', 'updates', 'to', 'table',
        'log', 'slave', 'updates', 'to', 'relay', 'log',
        'relay', 'log', 'relay', 'log', 'index', 'relay', 'log', 'info', 'file',
        'relay', 'log', 'recover', 'relay', 'log', 'purge', 'threshold',
        'relay', 'log', 'space', 'limit', 'sync', 'relay', 'log',
        'master', 'info', 'file', 'relay', 'log', 'info', 'file',
        'slave', 'load', 'tmp', 'dir', 'slave', 'exec', 'mode',
        'slave', 'parallel', 'type', 'slave', 'parallel', 'workers',
        'slave', 'preserve', 'commit', 'order', 'slave', 'checkpoint', 'period',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'group', 'max',
        'slave', 'checkpoint', 'algorithm', 'slave', 'checkpoint', 'algorithm',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'algorithm',
        'slave', 'parallel', 'type', 'slave', 'parallel', 'workers',
        'slave', 'preserve', 'commit', 'order', 'slave', 'checkpoint', 'period',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'algorithm',
        'transaction', 'isolation', 'level', 'innodb', 'read', 'committed',
        'innodb', 'repeatable', 'read', 'innodb', 'serializable', 'innodb',
        'autocommit', 'innodb', 'flush', 'log', 'at', 'trx', 'commit',
        'innodb', 'flush', 'log', 'wait', 'timeout', 'innodb', 'lock', 'wait',
        'timeout', 'innodb', 'deadlock', 'detect', 'long', 'wait', 'timeout',
        'innodb', 'read', 'only', 'innodb', 'undo', 'tablespaces',
        'max', 'allowed', 'packet', 'size', 'query', 'cache', 'type',
        'query', 'cache', 'size', 'query', 'cache', 'limit',
        'query', 'cache', 'min', 'res', 'unit', 'query', 'cache', 'wlock',
        'invalidate', 'table', 'open', 'cache', 'table', 'definition', 'cache',
        'tmp', 'table', 'size', 'max', 'heap', 'table', 'size',
        'max', 'connections', 'max', 'user', 'connections',
        'connect', 'timeout', 'wait', 'timeout', 'interactive', 'timeout',
        'lock', 'wait', 'timeout', 'lock', 'wait', 'exponent',
        'default', 'storage', 'engine', 'default', 'tmp', 'storage', 'engine',
        'max', 'execution', 'time', 'max', 'execution', 'time', 'for', 'write',
        'operations', 'max', 'execution', 'time', 'for', 'read', 'operations',
        'optimizer', 'trace', 'optimizer', 'trace', 'max', 'mem', 'size',
        'optimizer', 'trace', 'offset', 'optimizer', 'trace', 'enabled',
        'optimizer', 'trace', 'enabled', 'for', 'queries', 'cost',
        'optimizer', 'trace', 'enabled', 'for', 'queries', 'cost', 'threshold',
        'log', 'error', 'log', 'error', 'verbosity', 'log', 'error', 'rate',
        'limit', 'log', 'error', 'rate', 'limit', 'burst', 'log', 'error',
        'log', 'warnings', 'log', 'errors', 'log', 'warnings', 'log', 'errors',
        'general', 'log', 'general', 'log', 'file', 'general', 'log', 'slow',
        'slow', 'query', 'log', 'slow', 'query', 'log', 'file',
        'long', 'query', 'time', 'slow', 'query', 'log', 'always',
        'slow', 'query', 'log', 'min', 'examined', 'row', 'limit',
        'log', 'queries', 'not', 'using', 'indexes', 'log', 'throttle', 'queries',
        'not', 'using', 'indexes', 'log', 'raw', 'slow', 'query', 'log',
        'log', 'output', 'log', 'output', 'slow', 'query', 'log', 'output',
        'expire', 'logs', 'days', 'max', 'binlog', 'size', 'sync', 'binlog',
        'binlog', 'cache', 'size', 'max', 'binlog', 'cache', 'size',
        'binlog', 'stmt', 'cache', 'size', 'max', 'binlog', 'stmt', 'cache', 'size',
        'binlog', 'format', 'binlog', 'row', 'image', 'binlog', 'row', 'event',
        'max', 'size', 'binlog', 'transaction', 'compress', 'binlog', 'checksum',
        'binlog', 'rows', 'query', 'log', 'events', 'binlog', 'rows', 'query',
        'log', 'events', 'minimal', 'binlog', 'rows', 'query', 'log', 'events',
        'verbose', 'binlog', 'rows', 'query', 'log', 'events', 'include',
        'binlog', 'rows', 'query', 'log', 'events', 'exclude',
        'binlog', 'direct_non_transactional_updates', 'binlog', 'error',
        'action', 'binlog', 'group_commit_sync_delay',
        'binlog', 'group_commit_sync_no_delay_count',
        'log', 'slave', 'updates', 'log', 'slave', 'updates', 'to', 'table',
        'log', 'slave', 'updates', 'to', 'relay', 'log',
        'relay', 'log', 'relay', 'log', 'index', 'relay', 'log', 'info', 'file',
        'relay', 'log', 'recover', 'relay', 'log', 'purge', 'threshold',
        'relay', 'log', 'space', 'limit', 'sync', 'relay', 'log',
        'master', 'info', 'file', 'relay', 'log', 'info', 'file',
        'slave', 'load', 'tmp', 'dir', 'slave', 'exec', 'mode',
        'slave', 'parallel', 'type', 'slave', 'parallel', 'workers',
        'slave', 'preserve', 'commit', 'order', 'slave', 'checkpoint', 'period',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'group', 'max',
        'slave', 'checkpoint', 'algorithm', 'slave', 'checkpoint', 'algorithm',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'algorithm',
        'slave', 'parallel', 'type', 'slave', 'parallel', 'workers',
        'slave', 'preserve', 'commit', 'order', 'slave', 'checkpoint', 'period',
        'slave', 'checkpoint', 'group', 'max', 'slave', 'checkpoint', 'algorithm',
        'transaction', 'isolation', 'level', 'innodb', 'read', 'committed',
        'innodb', 'repeatable', 'read', 'innodb', 'serializable', 'innodb',
        'autocommit', 'innodb', 'flush', 'log', 'at', 'trx', 'commit',
        'innodb', 'flush', 'log', 'wait', 'timeout', 'innodb', 'lock', 'wait',
        'timeout', 'innodb', 'deadlock', 'detect', 'long', 'wait', 'timeout',
        'innodb', 'read', 'only', 'innodb', 'undo', 'tablespaces'
    }
    return name.lower() in reserved_words


def calculate_dependency_depth(module_name, dependencies):
    """
    计算模块的依赖链深度
    
    Args:
        module_name (str): 模块名称
        dependencies (list): 依赖关系列表
        
    Returns:
        int: 依赖链深度
    """
    visited = set()
    depth = 0
    
    def dfs(current, current_depth):
        nonlocal depth
        if current_depth > depth:
            depth = current_depth
        if current in visited:
            return
        visited.add(current)
        
        for dep in dependencies:
            if dep["from"] == current:
                dfs(dep["to"], current_depth + 1)
    
    dfs(module_name, 0)
    return depth


def read_artifact(artifact_ref):
    """
    读取制品内容（占位实现，实际使用时替换为具体实现）
    
    Args:
        artifact_ref (str): 制品引用
        
    Returns:
        dict: 制品内容
    """
    # 实际实现中，这里会从制品管理系统读取
    # 此处为占位实现
    return {}


def parse_prd(prd_content):
    """
    解析 PRD 文档（占位实现，实际使用时替换为具体实现）
    
    Args:
        prd_content (dict): PRD 内容
        
    Returns:
        dict: 解析后的 PRD 结构
    """
    return prd_content


def parse_arch_spec(arch_spec_content):
    """
    解析架构规格书（占位实现，实际使用时替换为具体实现）
    
    Args:
        arch_spec_content (dict): 架构规格书内容
        
    Returns:
        dict: 解析后的架构规格书结构
    """
    return arch_spec_content


def parse_openapi(openapi_content):
    """
    解析 OpenAPI 规范（占位实现，实际使用时替换为具体实现）
    
    Args:
        openapi_content (dict): OpenAPI 内容
        
    Returns:
        dict: 解析后的 OpenAPI 结构
    """
    return openapi_content


def parse_ddl(ddl_content):
    """
    解析 DDL 脚本（占位实现，实际使用时替换为具体实现）

    Args:
        ddl_content (dict): DDL 内容

    Returns:
        dict: 解析后的 DDL 结构
    """
    return ddl_content


def resolve_review_dir(stage_dir, stage_prefix):
    """
    动态确定审查报告目录路径，不存在则创建。

    规则见 docs/AGENTS.md「审查报告子目录 → 动态编号规则」：
    1. 已存在 *-审查报告 目录则复用
    2. 否则取同级最大序号 +1 新建

    Args:
        stage_dir (str): 如 "docs/02设计阶段"
        stage_prefix (str): 如 "02"（用于 {阶段}-{NN} 形式）；01 阶段传 "01" 且使用 {NN} 形式

    Returns:
        str: 审查报告目录完整路径
    """
    import os
    import re

    # 1. 先看是否已有 *-审查报告 目录，有则直接复用
    if os.path.isdir(stage_dir):
        for name in os.listdir(stage_dir):
            if name.endswith("-审查报告"):
                return os.path.join(stage_dir, name)

    # 2. 扫描同级序号，取最大值 +1
    max_seq = 0
    pattern = re.compile(rf'^{re.escape(stage_prefix)}-(\d+)-')
    if os.path.isdir(stage_dir):
        for name in os.listdir(stage_dir):
            m = pattern.match(name)
            if m:
                max_seq = max(max_seq, int(m.group(1)))

    next_seq = max_seq + 1
    if stage_prefix != "01":
        dir_name = f"{stage_prefix}-{next_seq:02d}-审查报告"
    else:
        dir_name = f"{next_seq:02d}-审查报告"

    full = os.path.join(stage_dir, dir_name)
    os.makedirs(full, exist_ok=True)
    return full
