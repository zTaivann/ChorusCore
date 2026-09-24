package dev.chorus.core.mail;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface MailRepository {

    void createTables() throws SQLException;

    void send(UUID recipient, String sender, String body, long sentAt) throws SQLException;

    List<Mail> inbox(UUID recipient) throws SQLException;

    int count(UUID recipient) throws SQLException;

    int unread(UUID recipient) throws SQLException;

    void markRead(UUID recipient) throws SQLException;

    /** @return how many letters went. */
    int clear(UUID recipient) throws SQLException;

    boolean clear(UUID recipient, long id) throws SQLException;

    /** Drops letters older than the number of days given. Zero keeps them for ever. */
    void prune(int keepDays) throws SQLException;
}
