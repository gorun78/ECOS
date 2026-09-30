package com.chinacreator.gzcm.engine.data.datasource.storage.adapter.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * F02-13（详细设计-02）建连桥：把「具体建连动作用」从存储适配器剥离到
 * runtime-access {@code JdbcConnector}（全仓 DriverManager 唯一合法白名单所在模块）。
 *
 * <p>适配器仍按方言构建 JDBC URL（{@code buildConnectionUrl}），
 * 但落地的 {@code DriverManager.getConnection} 调用由此桥完成——
 * 桥缺失时 {@code BaseJdbcAdapter#connect} 直接 fail-fast，禁止回退裸 DriverManager。</p>
 */
@FunctionalInterface
public interface AdapterConnectionBridge {

    Connection open(String jdbcUrl, String username, String password) throws SQLException;
}
