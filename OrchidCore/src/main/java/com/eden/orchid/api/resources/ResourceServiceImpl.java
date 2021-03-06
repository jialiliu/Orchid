package com.eden.orchid.api.resources;

import com.eden.common.util.EdenUtils;
import com.eden.orchid.api.OrchidContext;
import com.eden.orchid.api.compilers.OrchidParser;
import com.eden.orchid.api.options.annotations.Description;
import com.eden.orchid.api.resources.resource.ExternalResource;
import com.eden.orchid.api.resources.resource.FileResource;
import com.eden.orchid.api.resources.resource.OrchidResource;
import com.eden.orchid.api.resources.resourceSource.LocalResourceSource;
import com.eden.orchid.api.resources.resourceSource.OrchidResourceSource;
import com.eden.orchid.api.resources.resourceSource.PluginResourceSource;
import com.eden.orchid.api.theme.pages.OrchidReference;
import com.eden.orchid.utilities.OrchidUtils;
import com.google.inject.name.Named;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @since v1.0.0
 * @orchidApi services
 */
@Singleton
@Description(value = "How Orchid locates resources.", name = "Resources")
public final class ResourceServiceImpl implements ResourceService {

    private OrchidContext context;
    private List<LocalResourceSource> fileResourceSources;
    private List<PluginResourceSource> pluginResourceSources;
    private OkHttpClient client;

    private final String resourcesDir;

    @Inject
    public ResourceServiceImpl(
            @Named("src") String resourcesDir,
            Set<LocalResourceSource> fileResourceSources,
            Set<PluginResourceSource> pluginResourceSources,
            OkHttpClient client) {

        this.fileResourceSources = fileResourceSources.stream().sorted().collect(Collectors.toList());
        this.pluginResourceSources = pluginResourceSources.stream().sorted().collect(Collectors.toList());

        this.client = client;
        this.resourcesDir = resourcesDir;
    }

    @Override
    public void initialize(OrchidContext context) {
        this.context = context;
    }

// Load many datafiles into a single map
//----------------------------------------------------------------------------------------------------------------------

    @Override
    public Map<String, Object> getDatafile(final String fileName) {
        return context.getParserExtensions().stream()
                .map(ext -> {
                    OrchidResource resource = getLocalResourceEntry(fileName + "." + ext);
                    if (resource != null) {
                        String content = resource.getContent();

                        if (!EdenUtils.isEmpty(content)) {
                            return context.parse(ext, content);
                        }
                    }

                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<String, Object> getDatafiles(final String directory) {
        String[] parserExtensions = new String[context.getParserExtensions().size()];
        context.getParserExtensions().toArray(parserExtensions);
        List<OrchidResource> files = getLocalResourceEntries(directory, parserExtensions, true);

        Map<String, Object> allDatafiles = new HashMap<>();

        for (OrchidResource file : files) {
            file.getReference().setUsePrettyUrl(false);
            Map<String, Object> fileData = context.parse(file.getReference().getExtension(), file.getContent());

            String innerPath = OrchidUtils.normalizePath(file.getReference().getPath().replaceAll(directory, ""));

            String[] filePathPieces = OrchidUtils.normalizePath(innerPath + "/" + file.getReference().getFileName()).split("/");

            addNestedDataToMap(allDatafiles, filePathPieces, fileData);
        }

        return allDatafiles;
    }

    private void addNestedDataToMap(Map<String, Object> allDatafiles, String[] pathPieces, Map<String, Object> fileData) {
        if (fileData != null && pathPieces.length > 0) {
            if(pathPieces.length > 1) {
                if(!allDatafiles.containsKey(pathPieces[0])) {
                    allDatafiles.put(pathPieces[0], new HashMap<String, Object>());
                }
                String[] newArray = Arrays.copyOfRange(pathPieces, 1, pathPieces.length);
                addNestedDataToMap((Map<String, Object>) allDatafiles.get(pathPieces[0]), newArray, fileData);
            }
            else {
                if (fileData.containsKey(OrchidParser.arrayAsObjectKey) && fileData.keySet().size() == 1) {
                    allDatafiles.put(pathPieces[0], fileData.get(OrchidParser.arrayAsObjectKey));
                }
                else {
                    if(allDatafiles.containsKey(pathPieces[0]) && (allDatafiles.get(pathPieces[0]) instanceof Map)) {
                        for(String key : fileData.keySet()) {
                            ((Map<String, Object>) allDatafiles.get(pathPieces[0])).put(key, fileData.get(key));
                        }
                    }
                    else {
                        allDatafiles.put(pathPieces[0], fileData);
                    }
                }
            }
        }
    }

// Get a single resource from an exact filename
//----------------------------------------------------------------------------------------------------------------------

    @Override
    public OrchidResource getLocalResourceEntry(final String fileName) {
        return fileResourceSources
                .stream()
                .map(source -> source.getResourceEntry(fileName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public OrchidResource getThemeResourceEntry(final String fileName) {
        return context.getTheme().getResourceEntry(fileName);
    }

    @Override
    public OrchidResource getResourceEntry(final String fileName) {
        OrchidResource resource = null;

        // If the fileName looks like an external resource, return a Resource pointing to that resource
        if(OrchidUtils.isExternal(fileName)) {
            OrchidReference ref = OrchidReference.fromUrl(context, FilenameUtils.getName(fileName), fileName);
            resource = new ExternalResource(ref);
        }

        // If not external, check for a resource in any specified local resource sources
        if (resource == null) {
            resource = getLocalResourceEntry(fileName);
        }

        // If nothing found in local resources, check the theme
        if (resource == null) {
            resource = getThemeResourceEntry(fileName);
        }

        // If nothing found in the theme, check the default resource sources
        if (resource == null) {
            resource = pluginResourceSources
                    .stream()
                    .map(source -> source.getResourceEntry(fileName))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        // return the resource if found, otherwise null
        return resource;
    }

// Get all matching resources
//----------------------------------------------------------------------------------------------------------------------

    @Override
    public List<OrchidResource> getLocalResourceEntries(String path, String[] fileExtensions, boolean recursive) {
        TreeMap<String, OrchidResource> entries = new TreeMap<>();

        addEntries(entries, fileResourceSources, path, fileExtensions, recursive);

        return new ArrayList<>(entries.values());
    }

    @Override
    public List<OrchidResource> getThemeResourceEntries(String path, String[] fileExtensions, boolean recursive) {
        TreeMap<String, OrchidResource> entries = new TreeMap<>();

        List<OrchidResourceSource> themeSources = new ArrayList<>();
        themeSources.add(context.getTheme());
        addEntries(entries, themeSources, path, fileExtensions, recursive);

        return new ArrayList<>(entries.values());
    }

    @Override
    public List<OrchidResource> getResourceEntries(String path, String[] fileExtensions, boolean recursive) {
        TreeMap<String, OrchidResource> entries = new TreeMap<>();

        // add entries from local sources
        addEntries(entries, fileResourceSources, path, fileExtensions, recursive);

        // add entries from theme
        List<OrchidResourceSource> themeSources = new ArrayList<>();
        themeSources.add(context.getTheme());
        addEntries(entries, themeSources, path, fileExtensions, recursive);

        // add entries from other sources
        addEntries(entries, pluginResourceSources, path, fileExtensions, recursive);

        return new ArrayList<>(entries.values());
    }

    private void addEntries(
            TreeMap<String, OrchidResource> entries,
            Collection<? extends OrchidResourceSource> sources,
            String path,
            String[] fileExtensions,
            boolean recursive
    ) {

        sources
                .stream()
                .filter(source -> source.getPriority() >= 0)
                .map(source -> source.getResourceEntries(path, fileExtensions, recursive))
                .filter(OrchidUtils.not(EdenUtils::isEmpty))
                .flatMap(Collection::stream)
                .forEach(resource -> {
                    String relative = OrchidUtils.getRelativeFilename(resource.getReference().getPath(), path);

                    String key = relative
                            + "/"
                            + resource.getReference().getFileName()
                            + "."
                            + resource.getReference().getOutputExtension();

                    if (entries.containsKey(key)) {
                        if (resource.getPriority() > entries.get(key).getPriority()) {
                            entries.put(key, resource);
                        }
                    }
                    else {
                        entries.put(key, resource);
                    }
                });
    }

// Load a file from a local or remote URL
//----------------------------------------------------------------------------------------------------------------------

    @Override
    public Map<String, Object> loadAdditionalFile(String url) {
        if (!EdenUtils.isEmpty(url) && url.trim().startsWith("file://")) {
            return loadLocalFile(url.replaceAll("file://", ""));
        }
        else {
            return loadRemoteFile(url);
        }
    }

    @Override
    public Map<String, Object> loadLocalFile(String url) {
        try {
            File file = new File(url);
            String s = IOUtils.toString(new FileInputStream(file), Charset.forName("UTF-8"));
            return context.parse("json", s);
        }
        catch (FileNotFoundException e) {
            // ignore files not being found
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Map<String, Object> loadRemoteFile(String url) {
        Request request = new Request.Builder().url(url).build();

        Map<String, Object> object = null;

        try(Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                object = context.parse("json", response.body().string());
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return object;
    }

// Find closest file
//----------------------------------------------------------------------------------------------------------------------

    @Override
    public OrchidResource findClosestFile(String filename) {
        return findClosestFile(filename, false);
    }

    @Override
    public OrchidResource findClosestFile(String filename, boolean strict) {
        return findClosestFile(filename, strict, 10);
    }

    @Override
    public OrchidResource findClosestFile(String filename, boolean strict, int maxIterations) {
        File folder = new File(resourcesDir);

        while (true) {
            if (folder.isDirectory()) {
                List<File> files = new ArrayList<>(FileUtils.listFiles(folder, null, false));

                for (File file : files) {
                    if (!strict) {
                        if (FilenameUtils.removeExtension(file.getName()).equalsIgnoreCase(filename)) {
                            return new FileResource(context, file);
                        }
                    }
                    else {
                        if (file.getName().equals(filename)) {
                            return new FileResource(context, file);
                        }
                    }
                }
            }

            // set the folder to its own parent and search again
            if (folder.getParentFile() != null && maxIterations > 0) {
                folder = folder.getParentFile();
                maxIterations--;
            }

            // there is no more parent to search, exit the loop
            else {
                break;
            }
        }

        return null;
    }


// Find first matching resource
//----------------------------------------------------------------------------------------------------------------------

    @Override
    public OrchidResource locateLocalResourceEntry(final String fileName) {
        return locateLocalResourceEntry(fileName, new ArrayList<>(context.getCompilerExtensions()));
    }

    @Override
    public OrchidResource locateLocalResourceEntry(final String fileName, String[] fileExtensions) {
        List<String> extensions = new ArrayList<>();
        Collections.addAll(extensions, fileExtensions);
        return locateLocalResourceEntry(fileName, extensions);
    }

    @Override
    public OrchidResource locateLocalResourceEntry(final String fileName, List<String> fileExtensions) {
        String fullFileName = OrchidUtils.normalizePath(fileName);
        if(!fullFileName.contains(".")) {
            for(String extension : fileExtensions) {
                String testFileName = fullFileName + "." + extension;
                OrchidResource resource = getLocalResourceEntry(testFileName);
                if(resource != null) {
                    return resource;
                }
            }
        }

        return getLocalResourceEntry(fullFileName);
    }

    private OrchidResource locateSinglePage(String templateName, String extension) {
        String fullFileName = OrchidUtils.normalizePath(templateName);

        if(!fullFileName.contains(".")) {
            fullFileName = fullFileName + "." + extension;
        }

        return context.getResourceEntry(fullFileName);
    }




    @Override
    public OrchidResource locateTemplate(String fileNames) {
        if(fileNames.startsWith("?")) {
            return locateTemplate(StringUtils.stripStart(fileNames, "?"), true);
        }
        else {
            return locateTemplate(fileNames, false);
        }
    }

    @Override
    public OrchidResource locateTemplate(final String[] fileNames) {
        return locateTemplate(fileNames, true);
    }

    @Override
    public OrchidResource locateTemplate(List<String> fileNames) {
        return locateTemplate(fileNames, true);
    }

    @Override
    public OrchidResource locateTemplate(String fileNames, boolean ignoreMissing) {
        return locateTemplate(fileNames.split(","), ignoreMissing);
    }

    @Override
    public OrchidResource locateTemplate(final String[] fileNames, boolean ignoreMissing) {
        List<String> fileNamesList = new ArrayList<>();
        Collections.addAll(fileNamesList, fileNames);
        return locateTemplate(fileNamesList, ignoreMissing);
    }

    @Override
    public OrchidResource locateTemplate(final List<String> fileNames, boolean ignoreMissing) {
        for(String template : fileNames) {
            OrchidResource resource = locateSingleTemplate(template);
            if(resource != null) {
                return resource;
            }
        }

        if(ignoreMissing) {
            return null;
        }
        else {
            throw new IllegalArgumentException("Could not find template in list \"" + fileNames + "\"");
        }
    }

    private OrchidResource locateSingleTemplate(String templateName) {
        String fullFileName = OrchidUtils.normalizePath(OrchidUtils.normalizePath(templateName));

        if(!fullFileName.startsWith("templates/")) {
            fullFileName = "templates/" + fullFileName;
        }
        if(!fullFileName.contains(".")) {
            fullFileName = fullFileName + "." + context.getTheme().getPreferredTemplateExtension();
        }

        return context.getResourceEntry(fullFileName);
    }

}
