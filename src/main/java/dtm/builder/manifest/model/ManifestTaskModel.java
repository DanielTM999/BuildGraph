package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestTaskModel {

    private String id;
    private String name;

    private String phase;

    private String when = "before";
    private int order;
    private String command;
    private String workingDir;
    private boolean failOnError = true;

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "args")
    @JacksonXmlProperty(localName = "arg")
    private List<String> args = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "dependsOn")
    @JacksonXmlProperty(localName = "dependency")
    private List<String> dependsOn = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, String> env = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args != null ? args : new ArrayList<>();
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn != null ? dependsOn : new ArrayList<>();
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env != null ? env : new LinkedHashMap<>();
    }

    public String identity() {
        if (id != null && !id.isBlank()) {
            return id;
        }
        if (name != null && !name.isBlank()) {
            return name;
        }
        return command == null ? "" : command;
    }
}
