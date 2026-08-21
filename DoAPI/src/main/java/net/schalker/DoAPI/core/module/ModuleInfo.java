package net.schalker.DoAPI.core.module;

public class ModuleInfo {

    private final String name;
    private final String version;
    private final String author;
    private final String description;

    public ModuleInfo(String name, String version, String author, String description) {
        this.name = name == null ? "Unknown" : name;
        this.version = version == null ? "1.0.0" : version;
        this.author = author == null ? "Unknown" : author;
        this.description = description == null ? "" : description;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name + " v" + version + " by " + author;
    }
}
