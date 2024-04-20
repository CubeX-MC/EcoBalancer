package org.cubexmc.ecobalancer.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class StatementWriter implements AutoCloseable {
    private int writerIndex = 0;
    private final PreparedStatement statement;

    private StatementWriter(final PreparedStatement statement) {
        this.statement=Objects.requireNonNull(statement, "Statement cannot be null!");
    }

    public static StatementWriter newWriter(final PreparedStatement statement) {
        return new StatementWriter(statement);
    }

    public static StatementWriter newWriter(final Connection connection, final String sql) throws SQLException {
        return new StatementWriter(connection.prepareStatement(sql));
    }

    public StatementWriter setWriterIndex(final int index) {
        this.writerIndex=index;
        return this;
    }

    public int getWriterIndex() {
        return writerIndex;
    }

    @FunctionalInterface
    private interface IntBiConsumer<T> {
        void apply(final int i, final T var) throws SQLException;
    }

    private <T> StatementWriter write(final T value, final IntBiConsumer<T> consumer) throws SQLException {
        consumer.apply(writerIndex++, value);
        return this;
    }

    public StatementWriter writeInt(final int x) throws SQLException {
        return write(x, statement::setInt);
    }

    public StatementWriter writeString(final String string) throws SQLException {
        return write(string, statement::setString);
    }

    public StatementWriter writeDouble(final double x) throws SQLException {
        return write(x, statement::setDouble);
    }

    public StatementWriter writeLong(final long x) throws SQLException {
        return write(x, statement::setLong);
    }

    public StatementWriter writeBoolean(final boolean b) throws SQLException {
        return write(b, statement::setBoolean);
    }

    public int executeUpdate() throws SQLException {
        return statement.executeUpdate();
    }

    public boolean execute(final String sql) throws SQLException {
        return statement.execute(sql);
    }

    @Override
    public void close() throws Exception {
        statement.close();
    }

    public PreparedStatement getStatement() {
        return statement;
    }
}
