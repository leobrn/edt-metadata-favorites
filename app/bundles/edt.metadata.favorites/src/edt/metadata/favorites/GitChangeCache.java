/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonViewer;

import com._1c.g5.v8.dt.common.git.GitUtils;
import com._1c.g5.v8.dt.core.platform.IResourceLookup;
import com._1c.g5.wiring.ServiceAccess;


final class GitChangeCache
    implements GitChangesQuery
{
    private static final long REFRESH_DELAY_MILLIS = 150;

    private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000;

    private static final long MAX_RETRY_DELAY_MILLIS = 60_000;

    private static final int NAVIGATOR_REFRESH_DELAY_MILLIS = 100;

    private final Map<Path, Entry> entries = new HashMap<>();

    private final Object jobFamily = new Object();

    private final AtomicBoolean navigatorRefreshScheduled = new AtomicBoolean();

    private final IResourceChangeListener resourceChangeListener = this::resourcesChanged;

    private final ListenerHandle indexChangedListener;

    private final ListenerHandle refsChangedListener;

    private final ListenerHandle workingTreeModifiedListener;

    private volatile boolean disposed;

    GitChangeCache()
    {
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
            resourceChangeListener, IResourceChangeEvent.POST_CHANGE);
        indexChangedListener = Repository.getGlobalListenerList().addIndexChangedListener(
            event -> invalidate(event.getRepository()));
        refsChangedListener = Repository.getGlobalListenerList().addRefsChangedListener(
            event -> invalidate(event.getRepository()));
        workingTreeModifiedListener = Repository.getGlobalListenerList()
            .addWorkingTreeModifiedListener(event -> invalidate(event.getRepository()));
    }

    @Override
    public GitChangeState getState(Object element)
    {
        IProject project = MetadataPinSupport.getProject(element);
        if (project == null)
        {
            return GitChangeState.OUTSIDE_PROJECT;
        }

        Repository repository = GitUtils.getGitRepository(project);
        if (repository == null)
        {
            return GitChangeState.CLEAN;
        }

        Entry entry;
        synchronized (this)
        {
            Path key = repositoryKey(repository);
            entry = entries.computeIfAbsent(key, ignored -> new Entry());
            entry.repository = repository;
            scheduleIfNeeded(key, entry);
            if (!entry.available)
            {
                return GitChangeState.UNKNOWN;
            }
            return entry.snapshot.hasChanges() ? GitChangeState.DIRTY : GitChangeState.CLEAN;
        }
    }

    @Override
    public boolean isChanged(Object element)
    {
        if (!(element instanceof EObject eObject) || MetadataPinSupport.isConfigurationRoot(element))
        {
            return false;
        }

        IFile file = getPlatformResource(eObject);
        if (file == null)
        {
            return false;
        }

        Repository repository = GitUtils.getGitRepository(file);
        if (repository == null)
        {
            return false;
        }
        boolean ownsFile = ownsPlatformResource(eObject, file);
        Path ownDirectory = ownsFile ? null : nestedObjectDirectory(eObject, file);
        if (!ownsFile && ownDirectory == null)
        {
            return false;
        }
        synchronized (this)
        {
            Entry entry = entries.get(repositoryKey(repository));
            if (entry == null || !entry.available)
            {
                return false;
            }
            return ownsFile ? entry.snapshot.affects(file)
                : entry.snapshot.affectsDirectory(ownDirectory);
        }
    }

    /**
     * Элемент владеет файлом, если его контейнер отображается в другой файл. Иначе один файл
     * покрывает несколько узлов дерева (например, Configuration.mdo — корень конфигурации и все
     * группы типов метаданных), и изменение файла нельзя приписать конкретному узлу.
     */
    private boolean ownsPlatformResource(EObject object, IFile file)
    {
        EObject container = object.eContainer();
        if (container == null)
        {
            return true;
        }
        IFile containerFile = getPlatformResource(container);
        return containerFile == null || !file.equals(containerFile);
    }

    /**
     * Формы, команды и макеты описаны в mdo родителя, поэтому не владеют своим файлом, но имеют
     * собственный каталог рядом с ним: {@code <каталог родителя>/<Группа>/<Имя>}. Имя группы EDT
     * совпадает с именем содержащей ссылки модели с заглавной буквы: {@code forms} — {@code Forms}.
     */
    private static Path nestedObjectDirectory(EObject object, IFile platformResource)
    {
        EStructuralFeature feature = object.eContainmentFeature();
        IPath location = platformResource.getLocation();
        if (feature == null || location == null)
        {
            return null;
        }
        Path parent = location.toFile().toPath().toAbsolutePath().normalize().getParent();
        return nestedObjectDirectory(parent, feature.getName(),
            MetadataPinSupport.getObjectName(object));
    }

    static Path nestedObjectDirectory(Path containerDirectory, String featureName,
        String objectName)
    {
        if (containerDirectory == null || featureName == null || featureName.isEmpty()
            || objectName == null || objectName.isEmpty())
        {
            return null;
        }
        String group = Character.toUpperCase(featureName.charAt(0)) + featureName.substring(1);
        return containerDirectory.resolve(group).resolve(objectName);
    }

    /**
     * Оценка сверху числа изменённых объектов проекта по последнему snapshot, без обхода дерева
     * Навигатора. Возвращает значение больше limit, как только лимит превышен.
     */
    int countChangedObjects(Object element, int limit)
    {
        IProject project = MetadataPinSupport.getProject(element);
        if (project == null)
        {
            return 0;
        }
        IPath location = project.getLocation();
        if (location == null)
        {
            return 0;
        }
        Repository repository = GitUtils.getGitRepository(project);
        if (repository == null)
        {
            return 0;
        }
        synchronized (this)
        {
            Entry entry = entries.get(repositoryKey(repository));
            if (entry == null || !entry.available)
            {
                return 0;
            }
            return entry.snapshot.countChangedFilesUnder(location.toFile().toPath(), limit);
        }
    }

    boolean isSnapshotCurrent(Object element)
    {
        IProject project = MetadataPinSupport.getProject(element);
        if (project == null)
        {
            return true;
        }
        Repository repository = GitUtils.getGitRepository(project);
        if (repository == null)
        {
            return true;
        }
        synchronized (this)
        {
            Entry entry = entries.get(repositoryKey(repository));
            return entry != null && entry.available && !entry.stale && !entry.running;
        }
    }

    synchronized void dispose()
    {
        disposed = true;
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
        indexChangedListener.remove();
        refsChangedListener.remove();
        workingTreeModifiedListener.remove();
        Job.getJobManager().cancel(jobFamily);
        entries.clear();
    }

    synchronized void invalidateForFilterActivation()
    {
        if (disposed)
        {
            return;
        }
        for (Entry entry : entries.values())
        {
            entry.generation++;
            entry.stale = true;
            entry.available = false;
        }
    }

    private IFile getPlatformResource(EObject object)
    {
        try
        {
            return ServiceAccess.get(IResourceLookup.class).getPlatformResource(object);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private void resourcesChanged(IResourceChangeEvent event)
    {
        Set<Path> changedRepositories = changedRepositoryKeys(event.getDelta());
        if (changedRepositories == null)
        {
            invalidateAll();
        }
        else if (!changedRepositories.isEmpty())
        {
            invalidate(changedRepositories);
        }
    }

    private void invalidate(Repository repository)
    {
        if (repository != null)
        {
            invalidate(Set.of(repositoryKey(repository)));
        }
    }

    private void invalidate(Set<Path> keys)
    {
        boolean changed = false;
        synchronized (this)
        {
            if (disposed)
            {
                return;
            }
            for (Path key : keys)
            {
                Entry entry = entries.get(key);
                if (entry != null)
                {
                    entry.generation++;
                    entry.stale = true;
                    entry.failureCount = 0;
                    changed = true;
                }
            }
        }
        if (changed)
        {
            refreshNavigator();
        }
    }

    private void invalidateAll()
    {
        synchronized (this)
        {
            if (disposed)
            {
                return;
            }
            for (Entry entry : entries.values())
            {
                entry.generation++;
                entry.stale = true;
                entry.failureCount = 0;
            }
        }
        refreshNavigator();
    }

    private void scheduleIfNeeded(Path key, Entry entry)
    {
        if (disposed || entry.running || !entry.stale
            || entry.attemptedGeneration >= entry.generation)
        {
            return;
        }

        entry.running = true;
        long generation = entry.generation;
        Repository repository = entry.repository;
        Job job = new Job("Обновление изменённых объектов Git")
        {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                GitChangeSnapshot snapshot = null;
                Throwable failure = null;
                try
                {
                    snapshot = GitChangeSnapshot.from(repository, Git.wrap(repository).status()
                        .setProgressMonitor(new JobCancellationMonitor(monitor)).call());
                }
                catch (GitAPIException | RuntimeException e)
                {
                    failure = e;
                }
                complete(key, generation, snapshot, failure);
                return Status.OK_STATUS;
            }

            @Override
            public boolean belongsTo(Object family)
            {
                return family == jobFamily;
            }
        };
        job.setSystem(true);
        job.schedule(retryDelay(entry.failureCount));
    }

    private void complete(Path key, long generation, GitChangeSnapshot snapshot,
        Throwable failure)
    {
        synchronized (this)
        {
            Entry entry = entries.get(key);
            if (entry == null || disposed)
            {
                return;
            }
            entry.running = false;
            entry.repository = null;
            if (entry.generation == generation)
            {
                entry.attemptedGeneration = generation;
                if (failure == null)
                {
                    entry.snapshot = snapshot;
                    entry.available = true;
                    entry.stale = false;
                    entry.failureCount = 0;
                }
                else
                {
                    entry.stale = true;
                    entry.failureCount++;
                    entry.generation++;
                }
            }
        }

        if (failure != null)
        {
            Activator.logError("Не удалось получить изменения Git", failure);
        }
        refreshNavigator();
    }

    private static Set<Path> changedRepositoryKeys(IResourceDelta root)
    {
        if (root == null)
        {
            return null;
        }
        Set<IProject> changedProjects = new HashSet<>();
        try
        {
            root.accept(delta -> {
                IResource resource = delta.getResource();
                if (resource.isDerived(IResource.CHECK_ANCESTORS))
                {
                    return false;
                }
                if (resource.getType() != IResource.FILE)
                {
                    return true;
                }
                int kind = delta.getKind();
                int flags = delta.getFlags();
                boolean contentChanged = kind == IResourceDelta.ADDED
                    || kind == IResourceDelta.REMOVED
                    || kind == IResourceDelta.CHANGED
                        && (flags & (IResourceDelta.CONTENT | IResourceDelta.REPLACED
                            | IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO)) != 0;
                if (contentChanged)
                {
                    changedProjects.add(resource.getProject());
                }
                return false;
            });
        }
        catch (CoreException | RuntimeException e)
        {
            return null;
        }
        Set<Path> result = new HashSet<>();
        for (IProject project : changedProjects)
        {
            Repository repository = GitUtils.getGitRepository(project);
            if (repository != null)
            {
                result.add(repositoryKey(repository));
            }
        }
        return result;
    }

    private void refreshNavigator()
    {
        if (!navigatorRefreshScheduled.compareAndSet(false, true))
        {
            return;
        }
        Display display = Display.getDefault();
        if (display == null || display.isDisposed())
        {
            navigatorRefreshScheduled.set(false);
            return;
        }
        // timerExec допустим только из UI-потока, поэтому вход в UI выполняется через asyncExec.
        try
        {
            display.asyncExec(() -> scheduleNavigatorRefresh(display));
        }
        catch (SWTException e)
        {
            navigatorRefreshScheduled.set(false);
        }
    }

    private void scheduleNavigatorRefresh(Display display)
    {
        if (display.isDisposed())
        {
            navigatorRefreshScheduled.set(false);
            return;
        }
        try
        {
            display.timerExec(NAVIGATOR_REFRESH_DELAY_MILLIS, this::performNavigatorRefresh);
        }
        catch (SWTException e)
        {
            navigatorRefreshScheduled.set(false);
        }
    }

    private void performNavigatorRefresh()
    {
        navigatorRefreshScheduled.set(false);
        if (disposed || !PlatformUI.isWorkbenchRunning())
        {
            return;
        }
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows())
        {
            CommonViewer viewer = NavigatorAccess.findViewer(window).orElse(null);
            if (viewer != null && !viewer.getControl().isDisposed()
                && NavigatorAccess.isFilterActive(viewer, GitChangedOnlyFilter.class))
            {
                viewer.refresh();
                GitChangedAutoExpand.afterViewerRefresh(viewer);
            }
        }
    }

    static long retryDelay(int failureCount)
    {
        if (failureCount == 0)
        {
            return REFRESH_DELAY_MILLIS;
        }
        int exponent = Math.min(failureCount - 1, 6);
        return Math.min(INITIAL_RETRY_DELAY_MILLIS << exponent, MAX_RETRY_DELAY_MILLIS);
    }

    private static Path repositoryKey(Repository repository)
    {
        return repository.getDirectory().toPath().toAbsolutePath().normalize();
    }

    private static final class Entry
    {
        private GitChangeSnapshot snapshot;

        private long generation;

        private long attemptedGeneration = -1;

        private boolean available;

        private boolean running;

        private int failureCount;

        private Repository repository;

        private boolean stale = true;
    }

    private static final class JobCancellationMonitor
        implements ProgressMonitor
    {
        private final IProgressMonitor monitor;

        private JobCancellationMonitor(IProgressMonitor monitor)
        {
            this.monitor = monitor;
        }

        @Override
        public void start(int totalTasks)
        {
        }

        @Override
        public void beginTask(String title, int totalWork)
        {
        }

        @Override
        public void update(int completed)
        {
        }

        @Override
        public void endTask()
        {
        }

        @Override
        public boolean isCancelled()
        {
            return monitor.isCanceled();
        }

        @Override
        public void showDuration(boolean enabled)
        {
        }
    }
}
