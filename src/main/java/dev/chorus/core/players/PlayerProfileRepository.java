package dev.chorus.core.players;

import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface PlayerProfileRepository {

    void createTables() throws SQLException;

    /** Writes down the name and address of somebody who just arrived. */
    void seen(UUID player, String name, String address, long when) throws SQLException;

    /** Writes down where and when somebody left. */
    void left(UUID player, long when, String world, double x, double y, double z,
              float yaw, float pitch) throws SQLException;

    void nickname(UUID player, @Nullable String nickname) throws SQLException;

    @Nullable PlayerProfile find(UUID player) throws SQLException;

    /** By username first, then by nickname, both ignoring case. */
    @Nullable PlayerProfile findByName(String name) throws SQLException;

    /** Everyone else who has connected from the same address. */
    List<String> sharing(String address, UUID except) throws SQLException;

    /** Names for tab completion, newest first. */
    List<String> namesLike(String prefix, int limit) throws SQLException;
}
