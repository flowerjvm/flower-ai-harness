package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

record AgentCliWorkspace(
        Path runDirectory,
        Path requestFile,
        Path resultFile,
        Path stdoutLog,
        Path stderrLog,
        Path processFile
) {

    static AgentCliWorkspace create(AgentCliGatewayConfig config, String callId) throws IOException {
        Files.createDirectories(config.runWorkspaceRoot());
        Path runDirectory = config.runWorkspaceRoot()
                .resolve(callId)
                .toAbsolutePath()
                .normalize();
        if (!runDirectory.startsWith(config.runWorkspaceRoot())) {
            throw new IOException("Call workspace escaped configured root");
        }
        Files.createDirectory(runDirectory);
        setOwnerOnly(runDirectory, true);
        return new AgentCliWorkspace(
                runDirectory,
                runDirectory.resolve(config.requestFileName()),
                runDirectory.resolve(config.resultFileName()),
                runDirectory.resolve("stdout.log"),
                runDirectory.resolve("stderr.log"),
                runDirectory.resolve("process.json"));
    }

    static void setOwnerOnly(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX file systems use their platform defaults.
        }
    }
}
