/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;


public class PinStore
{
    private static final String FILE_NAME = "pinned.yaml";

    private static final String FORMAT_VERSION_HEADER = "formatVersion:";


    private static final int CURRENT_FORMAT_VERSION = 2;

    private static final String PINNED_PROJECTS_HEADER = "pinnedProjects:";

    private static final String PINNED_OBJECTS_HEADER = "pinnedObjects:";

    private static final String RECURSIVE_PINS_HEADER = "recursivePins:";

    private static final String RECURSIVE_EXCLUSIONS_HEADER = "recursiveExclusions:";

    private final Path storageFile;


    private final Set<String> pinnedProjects = new TreeSet<>();


    private final Set<PinnedObjectKey> pinnedObjects = new LinkedHashSet<>();


    private final Set<PinnedObjectKey> recursivePins = new LinkedHashSet<>();


    private final Set<PinnedObjectKey> recursiveExclusions = new LinkedHashSet<>();


    private final java.util.Map<PinnedObjectKey, String> objectLabels = new java.util.HashMap<>();

    private boolean loaded;


    private Set<String> projectsWithIndividualPins;

    private Set<String> projectsWithRecursivePins;


    private int modCount;


    public PinStore(IPath stateLocation)
    {
        this.storageFile = stateLocation.append(FILE_NAME).toFile().toPath();
    }


    public synchronized int getModCount()
    {
        return modCount;
    }


    public synchronized boolean isProjectPinned(String projectName)
    {
        if (!ensureLoaded())
        {
            return false;
        }
        return pinnedProjects.contains(projectName);
    }

    public synchronized void pinProject(String projectName)
    {
        if (!ensureLoaded())
        {
            return; // не смогли прочитать файл - не мутируем и не сохраняем, чтобы не затереть данные
        }
        if (pinnedProjects.add(projectName))
        {
            save();
        }
    }

    public synchronized void unpinProject(String projectName)
    {
        if (!ensureLoaded())
        {
            return;
        }
        if (pinnedProjects.remove(projectName))
        {
            save();
        }
    }

    public synchronized Set<String> getPinnedProjects()
    {
        if (!ensureLoaded())
        {
            return Set.of();
        }
        return Set.copyOf(pinnedProjects);
    }



    public synchronized boolean hasPinnedObjects(String projectName)
    {
        if (!ensureLoaded())
        {
            return false;
        }
        ensureProjectIndex();
        return projectsWithIndividualPins.contains(projectName)
            || projectsWithRecursivePins.contains(projectName);
    }

    public synchronized boolean isObjectPinned(String projectName, String uuid)
    {
        if (!ensureLoaded())
        {
            return false;
        }
        return pinnedObjects.contains(new PinnedObjectKey(projectName, uuid));
    }


    public synchronized boolean isObjectEffectivelyPinned(String projectName, Iterable<String> uuidPath)
    {
        if (!ensureLoaded())
        {
            return false;
        }
        boolean self = true;
        for (String uuid : uuidPath)
        {
            PinnedObjectKey key = new PinnedObjectKey(projectName, uuid);
            if (self && pinnedObjects.contains(key))
            {
                return true;
            }
            if (recursiveExclusions.contains(key))
            {
                return false;
            }
            if (recursivePins.contains(key))
            {
                return true;
            }
            self = false;
        }
        return false;
    }

    public synchronized boolean isRecursivePin(String projectName, String uuid)
    {
        return ensureLoaded() && recursivePins.contains(new PinnedObjectKey(projectName, uuid));
    }


    public synchronized List<PinTarget> getPinnedObjects(String projectName)
    {
        if (!ensureLoaded())
        {
            return List.of();
        }
        List<PinTarget> result = new ArrayList<>();
        for (PinnedObjectKey key : pinnedObjects)
        {
            if (key.projectName().equals(projectName))
            {
                result.add(new PinTarget(key.uuid(), objectLabels.get(key)));
            }
        }
        return List.copyOf(result);
    }

    public synchronized void pinObject(String projectName, PinTarget target)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        PinnedObjectKey key = new PinnedObjectKey(projectName, target.uuid());
        if (addObject(projectName, target) | recursiveExclusions.remove(key))
        {
            save();
        }
    }

    public synchronized void unpinObject(String projectName, String uuid)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        PinnedObjectKey key = new PinnedObjectKey(projectName, uuid);
        boolean changed = pinnedObjects.remove(key);
        changed |= recursivePins.remove(key);
        if (hasRecursivePins(projectName))
        {
            changed |= recursiveExclusions.add(key);
        }
        else
        {
            changed |= recursiveExclusions.removeIf(item -> item.projectName().equals(projectName));
        }
        if (changed)
        {
            objectLabels.remove(key);
            save();
        }
    }


    public synchronized void pinObjects(String projectName, Iterable<PinTarget> targets)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = false;
        for (PinTarget target : targets)
        {
            PinnedObjectKey key = new PinnedObjectKey(projectName, target.uuid());
            if (addObject(projectName, target) | recursiveExclusions.remove(key))
            {
                changed = true;
            }
        }
        if (changed)
        {
            save();
        }
    }


    public synchronized void pinBranch(String projectName, PinTarget root, Iterable<PinTarget> targets)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = recursivePins.add(new PinnedObjectKey(projectName, root.uuid()));
        for (PinTarget target : targets)
        {
            PinnedObjectKey key = new PinnedObjectKey(projectName, target.uuid());
            changed |= addObject(projectName, target);
            changed |= recursiveExclusions.remove(key);
        }
        if (changed)
        {
            save();
        }
    }


    public synchronized void unpinAllObjects(String projectName)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = pinnedObjects.removeIf(key -> key.projectName().equals(projectName));
        changed |= recursivePins.removeIf(key -> key.projectName().equals(projectName));
        changed |= recursiveExclusions.removeIf(key -> key.projectName().equals(projectName));
        if (changed)
        {
            objectLabels.keySet().removeIf(key -> key.projectName().equals(projectName));
            save();
        }
    }


    public synchronized void pruneMissingProjects(Set<String> existingProjectNames)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = pinnedProjects.removeIf(name -> !existingProjectNames.contains(name));
        if (pinnedObjects.removeIf(key -> !existingProjectNames.contains(key.projectName())))
        {
            objectLabels.keySet().removeIf(key -> !existingProjectNames.contains(key.projectName()));
            changed = true;
        }
        changed |= recursivePins.removeIf(key -> !existingProjectNames.contains(key.projectName()));
        changed |= recursiveExclusions.removeIf(key -> !existingProjectNames.contains(key.projectName()));
        if (changed)
        {
            save();
        }
    }


    public synchronized void pruneMissingObjects(String projectName, Set<String> existingUuids)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = pinnedObjects.removeIf(
            key -> key.projectName().equals(projectName) && !existingUuids.contains(key.uuid()));
        changed |= recursivePins.removeIf(
            key -> key.projectName().equals(projectName) && !existingUuids.contains(key.uuid()));
        changed |= recursiveExclusions.removeIf(
            key -> key.projectName().equals(projectName) && !existingUuids.contains(key.uuid()));
        if (objectLabels.keySet().removeIf(
            key -> key.projectName().equals(projectName) && !existingUuids.contains(key.uuid())))
        {
            changed = true;
        }
        if (changed)
        {
            save();
        }
    }


    public synchronized void unpinObjects(String projectName, Iterable<PinTarget> targets)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = false;
        List<PinnedObjectKey> keys = new ArrayList<>();
        for (PinTarget target : targets)
        {
            PinnedObjectKey key = new PinnedObjectKey(projectName, target.uuid());
            keys.add(key);
            if (pinnedObjects.remove(key) | recursivePins.remove(key))
            {
                objectLabels.remove(key);
                changed = true;
            }
        }
        if (hasRecursivePins(projectName))
        {
            for (PinnedObjectKey key : keys)
            {
                changed |= recursiveExclusions.add(key);
            }
        }
        else
        {
            changed |= recursiveExclusions.removeIf(item -> item.projectName().equals(projectName));
        }
        if (changed)
        {
            save();
        }
    }

    private boolean hasRecursivePins(String projectName)
    {
        ensureProjectIndex();
        return projectsWithRecursivePins.contains(projectName);
    }


    private void invalidateProjectIndex()
    {
        projectsWithIndividualPins = null;
        projectsWithRecursivePins = null;
    }


    private void ensureProjectIndex()
    {
        if (projectsWithIndividualPins != null)
        {
            return;
        }
        Set<String> individual = new HashSet<>();
        for (PinnedObjectKey key : pinnedObjects)
        {
            individual.add(key.projectName());
        }
        Set<String> recursive = new HashSet<>();
        for (PinnedObjectKey key : recursivePins)
        {
            recursive.add(key.projectName());
        }
        projectsWithIndividualPins = individual;
        projectsWithRecursivePins = recursive;
    }


    public synchronized void unpinBranch(String projectName, PinTarget root, Iterable<PinTarget> targets)
    {
        if (!ensureLoaded())
        {
            return;
        }
        invalidateProjectIndex();
        boolean changed = false;
        for (PinTarget target : targets)
        {
            PinnedObjectKey key = new PinnedObjectKey(projectName, target.uuid());
            changed |= pinnedObjects.remove(key);
            changed |= recursivePins.remove(key);
            changed |= recursiveExclusions.remove(key);
            objectLabels.remove(key);
        }
        changed |= recursiveExclusions.add(new PinnedObjectKey(projectName, root.uuid()));
        if (changed)
        {
            save();
        }
    }


    private boolean addObject(String projectName, PinTarget target)
    {
        PinnedObjectKey key = new PinnedObjectKey(projectName, target.uuid());
        boolean added = pinnedObjects.add(key);
        boolean labelChanged = false;
        if (target.fqn() != null)
        {
            String previousLabel = objectLabels.put(key, target.fqn());
            labelChanged = !Objects.equals(previousLabel, target.fqn());
        }
        return added || labelChanged;
    }



    private boolean ensureLoaded()
    {
        if (!loaded)
        {
            loaded = load();
        }
        return loaded;
    }


    private boolean load()
    {
        if (!Files.exists(storageFile))
        {
            return true;
        }

        Set<String> projects = new TreeSet<>();
        Set<PinnedObjectKey> objects = new LinkedHashSet<>();
        Set<PinnedObjectKey> recursive = new LinkedHashSet<>();
        Set<PinnedObjectKey> exclusions = new LinkedHashSet<>();
        java.util.Map<PinnedObjectKey, String> labels = new java.util.HashMap<>();
        String section = null;
        String pendingProject = null;
        String pendingUuid = null;
        boolean versionChecked = false;

        try (BufferedReader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank())
                {
                    continue;
                }

                if (!versionChecked)
                {
                    versionChecked = true;
                    if (line.startsWith(FORMAT_VERSION_HEADER))
                    {
                        int fileVersion;
                        try
                        {
                            fileVersion = Integer.parseInt(line.substring(FORMAT_VERSION_HEADER.length()).trim());
                        }
                        catch (NumberFormatException e)
                        {
                            Activator.logError(
                                "Не удалось разобрать версию формата файла закреплённых объектов: " + storageFile,
                                e);
                            return false;
                        }
                        if (fileVersion > CURRENT_FORMAT_VERSION)
                        {
                            Activator.logError("Файл закреплённых объектов " + storageFile + " записан версией "
                                + "формата " + fileVersion + ", а эта сборка плагина понимает только до "
                                + CURRENT_FORMAT_VERSION + " - чтение пропущено, чтобы не затереть файл при "
                                + "сохранении", null);
                            return false;
                        }
                        continue;
                    }
                }

                if (line.equals(PINNED_PROJECTS_HEADER))
                {
                    section = "projects";
                    continue;
                }
                if (line.equals(PINNED_OBJECTS_HEADER))
                {
                    section = "objects";
                    continue;
                }
                if (line.equals(RECURSIVE_PINS_HEADER))
                {
                    section = "recursive";
                    continue;
                }
                if (line.equals(RECURSIVE_EXCLUSIONS_HEADER))
                {
                    section = "exclusions";
                    continue;
                }

                if ("projects".equals(section) && line.startsWith("  - "))
                {
                    projects.add(unquote(line.substring(4).trim()));
                }
                else if ("objects".equals(section) || "recursive".equals(section)
                    || "exclusions".equals(section))
                {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("- project:"))
                    {
                        pendingProject = unquote(trimmed.substring("- project:".length()).trim());
                        pendingUuid = null;
                    }
                    else if (trimmed.startsWith("uuid:") && pendingProject != null)
                    {
                        pendingUuid = unquote(trimmed.substring("uuid:".length()).trim());
                        PinnedObjectKey key = new PinnedObjectKey(pendingProject, pendingUuid);
                        if ("objects".equals(section))
                        {
                            objects.add(key);
                        }
                        else if ("recursive".equals(section))
                        {
                            recursive.add(key);
                        }
                        else
                        {
                            exclusions.add(key);
                        }
                    }
                    else if ("objects".equals(section) && trimmed.startsWith("fqn:")
                        && pendingProject != null && pendingUuid != null)
                    {
                        String fqn = unquote(trimmed.substring("fqn:".length()).trim());
                        labels.put(new PinnedObjectKey(pendingProject, pendingUuid), fqn);
                    }
                }
            }
        }
        catch (IOException e)
        {
            Activator.logError("Не удалось прочитать файл закреплённых объектов: " + storageFile, e);
            return false;
        }

        pinnedProjects.clear();
        pinnedProjects.addAll(projects);
        pinnedObjects.clear();
        pinnedObjects.addAll(objects);
        recursivePins.clear();
        recursivePins.addAll(recursive);
        recursiveExclusions.clear();
        recursiveExclusions.addAll(exclusions);
        objectLabels.clear();
        objectLabels.putAll(labels);
        invalidateProjectIndex();
        modCount++;
        return true;
    }

    private synchronized void save()
    {
        modCount++;
        try
        {
            Files.createDirectories(storageFile.getParent());

            Path tempFile = Files.createTempFile(storageFile.getParent(), FILE_NAME, ".tmp");
            try
            {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8))
                {
                    writer.write(FORMAT_VERSION_HEADER + " " + CURRENT_FORMAT_VERSION);
                    writer.newLine();

                    writer.write(PINNED_PROJECTS_HEADER);
                    writer.newLine();
                    for (String project : pinnedProjects)
                    {
                        writer.write("  - " + quote(project));
                        writer.newLine();
                    }

                    writer.write(PINNED_OBJECTS_HEADER);
                    writer.newLine();
                    for (PinnedObjectKey key : pinnedObjects)
                    {
                        writer.write("  - project: " + quote(key.projectName()));
                        writer.newLine();
                        writer.write("    uuid: " + quote(key.uuid()));
                        writer.newLine();
                        String label = objectLabels.get(key);
                        if (label != null)
                        {
                            writer.write("    fqn: " + quote(label));
                            writer.newLine();
                        }
                    }

                    writeKeys(writer, RECURSIVE_PINS_HEADER, recursivePins);
                    writeKeys(writer, RECURSIVE_EXCLUSIONS_HEADER, recursiveExclusions);
                }

                try
                {
                    Files.move(tempFile, storageFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                }
                catch (AtomicMoveNotSupportedException e)
                {
                    Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            finally
            {
                Files.deleteIfExists(tempFile);
            }
        }
        catch (IOException e)
        {
            Activator.logError("Не удалось сохранить файл закреплённых объектов: " + storageFile, e);
        }
    }

    private static void writeKeys(BufferedWriter writer, String header, Set<PinnedObjectKey> keys)
        throws IOException
    {
        writer.write(header);
        writer.newLine();
        for (PinnedObjectKey key : keys)
        {
            writer.write("  - project: " + quote(key.projectName()));
            writer.newLine();
            writer.write("    uuid: " + quote(key.uuid()));
            writer.newLine();
        }
    }

    private static String quote(String value)
    {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String unquote(String value)
    {
        String result = value;
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\""))
        {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
