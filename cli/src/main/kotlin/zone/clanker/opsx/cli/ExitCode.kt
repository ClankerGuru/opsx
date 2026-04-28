/**
 * Named process exit codes used across all CLI commands so error handling
 * is consistent and scripts can switch on the numeric value.
 */
package zone.clanker.opsx.cli

/**
 * Process exit codes returned by opsx CLI commands.
 *
 * Ranges are grouped by domain:
 * - 0-2: generic success / failure / usage
 * - 10-19: change-related errors
 * - 20-29: host emission errors
 * - 30-39: project validation errors
 * - 40-49: self-update errors
 */
object ExitCode {
    /** Command completed successfully. */
    const val SUCCESS = 0

    /** Unspecified runtime failure. */
    const val FAILURE = 1

    /** Invalid arguments or missing required options. */
    const val USAGE_ERROR = 2

    /** The requested change directory does not exist under `opsx/changes/`. */
    const val CHANGE_NOT_FOUND = 10

    /** The change exists but is in a lifecycle state that forbids the requested operation. */
    const val CHANGE_WRONG_STATE = 11

    /** One or more host emitters failed to write their output files. */
    const val HOST_EMISSION_FAILED = 20

    /** A host-specific external command (e.g. `copilot plugin install`) returned non-zero. */
    const val HOST_EXTERNAL_COMMAND_FAILED = 21

    /** The current directory is not inside an opsx-managed workspace. */
    const val NOT_AN_OPSX_PROJECT = 30

    /** Could not reach the GitHub releases API during self-update. */
    const val SELF_UPDATE_NETWORK_FAILURE = 40

    /** Downloaded archive SHA-256 did not match the published checksum. */
    const val SELF_UPDATE_CHECKSUM_MISMATCH = 41
}
