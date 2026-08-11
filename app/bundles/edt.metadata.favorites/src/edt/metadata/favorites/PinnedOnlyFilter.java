/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;


public class PinnedOnlyFilter extends ViewerFilter
{
    public static final String ID = "edt.metadata.favorites.filter";


    private final Map<Object, Boolean> descendantCache = new IdentityHashMap<>();


    private boolean descendantCacheClearScheduled;


    private final Map<String, Boolean> hasPinnedObjectsCache = new HashMap<>();


    private long cachedRevision = Long.MIN_VALUE;


    private final FavoritesQuery favorites;

    public PinnedOnlyFilter()
    {
        this(Activator.getDefault().getPinStore());
    }

    PinnedOnlyFilter(FavoritesQuery favorites)
    {
        this.favorites = Objects.requireNonNull(favorites);
    }

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element)
    {
        syncRevision();

        String projectName = MetadataPinSupport.getProjectName(element);
        if (isPinnedElementCurrentRevision(element, projectName))
        {
            return true;
        }

        if (projectName != null && !hasPinnedObjects(projectName))
        {
            return false;
        }

        if (viewer instanceof AbstractTreeViewer treeViewer)
        {
            return hasPinnedDescendant(treeViewer, element);
        }

        return false;
    }

    boolean isPinnedElement(Object element)
    {
        syncRevision();
        String projectName = MetadataPinSupport.getProjectName(element);
        return isPinnedElementCurrentRevision(element, projectName);
    }

    private boolean isPinnedElementCurrentRevision(Object element, String projectName)
    {
        if (MetadataPinSupport.isProjectNode(element))
        {
            return projectName != null && favorites.isProjectPinned(projectName);
        }
        if (projectName == null)
        {
            return false;
        }
        if (favorites.isProjectPinned(projectName) && !hasPinnedObjects(projectName))
        {
            return true;
        }

        String uuid = MetadataPinSupport.getUuid(element);
        return uuid != null
            && favorites.isObjectEffectivelyPinned(
                projectName, MetadataPinSupport.getUuidPath(element));
    }


    private void syncRevision()
    {
        long revision = favorites.getRevision();
        if (revision != cachedRevision)
        {
            cachedRevision = revision;
            hasPinnedObjectsCache.clear();
            descendantCache.clear();
        }
    }


    private boolean hasPinnedObjects(String projectName)
    {
        Boolean value = hasPinnedObjectsCache.get(projectName);
        if (value == null)
        {
            value = favorites.hasPinnedObjects(projectName);
            hasPinnedObjectsCache.put(projectName, value);
        }
        return value;
    }

    private boolean hasPinnedDescendant(AbstractTreeViewer viewer, Object element)
    {
        Boolean cached = descendantCache.get(element);
        if (cached != null)
        {
            return cached;
        }

        boolean cacheable = ensureDescendantCacheClearScheduled(viewer);
        boolean result = computeHasPinnedDescendant(viewer, element);
        if (cacheable)
        {
            descendantCache.put(element, result);
        }
        return result;
    }


    private boolean ensureDescendantCacheClearScheduled(Viewer viewer)
    {
        if (descendantCacheClearScheduled)
        {
            return true;
        }
        Display display = displayOf(viewer);
        if (display == null)
        {
            return false;
        }
        descendantCacheClearScheduled = true;
        display.asyncExec(() -> {
            descendantCache.clear();
            descendantCacheClearScheduled = false;
        });
        return true;
    }

    private static Display displayOf(Viewer viewer)
    {
        Control control = viewer.getControl();
        if (control != null && !control.isDisposed())
        {
            return control.getDisplay();
        }
        return Display.getCurrent();
    }

    private boolean computeHasPinnedDescendant(AbstractTreeViewer viewer, Object element)
    {
        if (!(viewer.getContentProvider() instanceof ITreeContentProvider contentProvider))
        {
            return false;
        }

        Object[] children = contentProvider.getChildren(element);
        if (children == null)
        {
            return false;
        }

        for (Object child : children)
        {
            String childProject = MetadataPinSupport.getProjectName(child);
            if (MetadataPinSupport.isProjectNode(child) && childProject != null
                && favorites.isProjectPinned(childProject))
            {
                return true;
            }
            if (MetadataPinSupport.isProjectNode(child) && childProject != null
                && !hasPinnedObjects(childProject))
            {
                continue;
            }

            if (!MetadataPinSupport.isProjectNode(child) && childProject != null
                && favorites.isProjectPinned(childProject) && !hasPinnedObjects(childProject))
            {
                return true;
            }
            if (!MetadataPinSupport.isProjectNode(child) && childProject != null
                && !hasPinnedObjects(childProject))
            {
                continue;
            }

            String childUuid = MetadataPinSupport.getUuid(child);
            if (childUuid != null && childProject != null
                && favorites.isObjectEffectivelyPinned(childProject, MetadataPinSupport.getUuidPath(child)))
            {
                return true;
            }
            if (hasPinnedDescendant(viewer, child))
            {
                return true;
            }
        }
        return false;
    }
}
