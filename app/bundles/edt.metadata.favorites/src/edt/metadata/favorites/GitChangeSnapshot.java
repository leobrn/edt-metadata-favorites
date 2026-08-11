/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;


final class GitChangeSnapshot
{
    /**
     * Служебные файлы конфигурации, не относящиеся ни к одному объекту метаданных. Они лежат рядом
     * с {@code Configuration.mdo} и перезаписываются самой платформой, поэтому их изменение делает
     * «изменённым» каталог конфигурации целиком и не должно учитываться.
     */
    private static final Set<String> SERVICE_FILE_NAMES =
        Set.of("configuration.distr", "parentconfigurations.bin");

    private final Set<Path> changedFiles;

    private final Set<Path> changedDirectories;

    private GitChangeSnapshot(Set<Path> changedFiles, Set<Path> changedDirectories)
    {
        this.changedFiles = changedFiles;
        this.changedDirectories = changedDirectories;
    }

    static GitChangeSnapshot from(Repository repository, Status status)
    {
        Set<String> paths = new HashSet<>();
        paths.addAll(status.getAdded());
        paths.addAll(status.getChanged());
        paths.addAll(status.getRemoved());
        paths.addAll(status.getMissing());
        paths.addAll(status.getModified());
        paths.addAll(status.getUntracked());
        paths.addAll(status.getConflicting());
        return fromRelativePaths(repository.getWorkTree().toPath(), paths);
    }

    static GitChangeSnapshot fromRelativePaths(Path workTree, Collection<String> paths)
    {
        Path normalizedWorkTree = workTree.toAbsolutePath().normalize();
        Set<Path> changedFiles = new HashSet<>();
        Set<Path> changedDirectories = new HashSet<>();
        for (String path : paths)
        {
            Path changedFile = normalizedWorkTree.resolve(path).normalize();
            if (isServiceFile(changedFile))
            {
                continue;
            }
            changedFiles.add(changedFile);
            Path directory = changedFile.getParent();
            while (directory != null && directory.startsWith(normalizedWorkTree))
            {
                changedDirectories.add(directory);
                directory = directory.getParent();
            }
        }
        return new GitChangeSnapshot(changedFiles, changedDirectories);
    }

    private static boolean isServiceFile(Path file)
    {
        Path fileName = file.getFileName();
        return fileName != null
            && SERVICE_FILE_NAMES.contains(fileName.toString().toLowerCase(Locale.ROOT));
    }

    boolean hasChanges()
    {
        return !changedFiles.isEmpty();
    }

    int countChangedFilesUnder(Path directory, int limit)
    {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        int result = 0;
        for (Path changedFile : changedFiles)
        {
            if (changedFile.startsWith(normalizedDirectory))
            {
                result++;
                if (result > limit)
                {
                    return result;
                }
            }
        }
        return result;
    }

    boolean affects(IFile file)
    {
        IPath location = file.getLocation();
        if (location == null)
        {
            return false;
        }
        return affects(location.toFile().toPath());
    }

    boolean affectsDirectory(Path directory)
    {
        return changedDirectories.contains(directory.toAbsolutePath().normalize());
    }

    boolean affects(Path objectFile)
    {
        Path normalizedFile = objectFile.toAbsolutePath().normalize();
        if (changedFiles.contains(normalizedFile))
        {
            return true;
        }
        Path objectDirectory = normalizedFile.getParent();
        if (objectDirectory == null || objectDirectory.getFileName() == null
            || normalizedFile.getFileName() == null)
        {
            return false;
        }

        String fileName = normalizedFile.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        String objectName = extensionStart < 0 ? fileName : fileName.substring(0, extensionStart);
        String extension = extensionStart < 0 ? "" : fileName.substring(extensionStart + 1);
        boolean ownsDirectory = !"mdo".equalsIgnoreCase(extension)
            || objectName.equals(objectDirectory.getFileName().toString());
        if (ownsDirectory && changedDirectories.contains(objectDirectory))
        {
            return true;
        }
        // Файл описания объекта может лежать рядом с его каталогом, а не внутри него
        // (<Группа>/<Имя>.mdo и <Группа>/<Имя>/), поэтому проверяется и одноимённый соседний каталог.
        return changedDirectories.contains(objectDirectory.resolve(objectName));
    }

}
