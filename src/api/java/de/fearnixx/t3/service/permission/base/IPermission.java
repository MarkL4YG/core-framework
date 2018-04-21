package de.fearnixx.t3.service.permission.base;

/**
 * Created by MarkL4YG on 07-Feb-18
 */
public interface IPermission {

    /**
     * The string ID of the permission
     * Example:
     *   "i_channel_join_power"
     */
    String getSID();

    /**
     * The ID of the permission where values originate from
     * Example:
     *   "teamspeak"
     */
    String getSystemID();

    /**
     * Fully qualified ID. In form:
     *   "\<system\>:\<sid\>"
     * Example:
     *   "teamspeak:i_channel_join_power"
     */
    String getFullyQualifiedID();

    /**
     * The actual value of the permission
     */
    Integer getValue();
}