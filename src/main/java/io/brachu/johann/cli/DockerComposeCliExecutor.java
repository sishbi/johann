package io.brachu.johann.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.brachu.johann.ContainerId;
import io.brachu.johann.PortBinding;
import io.brachu.johann.Protocol;
import io.brachu.johann.cli.exception.ExecutionTimedOutException;
import io.brachu.johann.cli.exception.NonZeroExitCodeException;
import io.brachu.johann.exception.DockerComposeException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DockerComposeCliExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerComposeCliExecutor.class);

    private static final String[] UP_COMMAND = { "up", "-d" };
    private static final String[] DOWN_COMMAND = { "down" };
    private static final String[] KILL_COMMAND = { "kill" };
    private static final String[] PORT_COMMAND = { "port" };
    private static final String[] PS_COMMAND = { "ps", "-q" };

    private final String projectName;
    private final String composeFileContent;

    private final String[] upCmd;
    private final String[] downCmd;
    private final String[] killCmd;
    private final String[] portCmd;
    private final String[] psCmd;

    private final String[] env;

    DockerComposeCliExecutor(String executablePath, File composeFile, String projectName, Map<String, String> env) {
        String[] cmdPrefix = new String[] { executablePath, "-f", "-", "-p", projectName };

        this.projectName = projectName;
        composeFileContent = readComposeFile(composeFile);

        upCmd = concat(cmdPrefix, UP_COMMAND);
        downCmd = concat(cmdPrefix, DOWN_COMMAND);
        killCmd = concat(cmdPrefix, KILL_COMMAND);
        portCmd = concat(cmdPrefix, PORT_COMMAND);
        psCmd = concat(cmdPrefix, PS_COMMAND);

        this.env = mapToEnvArray(env);
    }

    public String getProjectName() {
        return projectName;
    }

    public void up() {
        log.debug("Starting cluster");
        exec(upCmd);
    }

    public void down() {
        log.debug("Shutting down cluster");
        exec(downCmd);
        log.debug("Cluster shut down");
    }

    public void kill() {
        log.debug("Killing cluster");
        exec(killCmd);
        log.debug("Cluster killed");
    }

    public PortBinding binding(String containerName, Protocol protocol, int privatePort) {
        String[] params = { "--protocol", protocol.toString(), containerName, String.valueOf(privatePort) };
        String binding = exec(concat(portCmd, params));
        return new PortBinding(binding);
    }

    public List<ContainerId> ps() {
        String[] ids = exec(psCmd).split(System.lineSeparator());
        return Arrays.stream(ids).filter(StringUtils::isNotBlank).map(ContainerId::new).collect(Collectors.toList());
    }

    private String exec(String[] cmd) {
        String cmdJoined = Arrays.stream(cmd).collect(Collectors.joining(" "));

        try {
            return CliRunner.exec(cmd, env, this::pipeComposeFile);
        } catch (IOException e) {
            throw new DockerComposeException("Unexpected I/O exception while executing '" + cmdJoined + "'.", e);
        } catch (InterruptedException e) {
            throw new DockerComposeException("Interrupted unexpectedly while executing '" + cmdJoined + "'.");
        } catch (NonZeroExitCodeException e) {
            String msg = String.format("Non-zero (%d) exit code returned from '%s'.%nOutput is:%n%s", e.getExitCode(), cmdJoined, e.getOutput());
            throw new DockerComposeException(msg);
        } catch (ExecutionTimedOutException e) {
            throw new DockerComposeException("Timed out while waiting for '" + cmdJoined + "' to finish executing.");
        }
    }

    private String readComposeFile(File composeFile) {
        try {
            return IOUtils.toString(new FileInputStream(composeFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DockerComposeException("Unexpected exception while reading compose file contents.", e);
        }
    }

    private void pipeComposeFile(Process process) {
        OutputStream output = process.getOutputStream();
        try {
            IOUtils.write(composeFileContent, output, StandardCharsets.UTF_8);
            output.flush();
            output.close();
        } catch (IOException e) {
            throw new DockerComposeException("Unexpected exception while piping compose file contents to docker-compose CLI.", e);
        }
    }

    private String[] mapToEnvArray(Map<String, String> env) {
        return env.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
    }

    private String[] concat(String[] first, String[] second) {
        return Stream.concat(Arrays.stream(first), Arrays.stream(second)).toArray(String[]::new);
    }

}
