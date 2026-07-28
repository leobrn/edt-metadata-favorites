/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonViewer;


public final class PinOperations
{
    private static final int EXPAND_LEVELS = 3;

    private PinOperations()
    {
    }


    public static void togglePin(Object element)
    {
        PinStore store = Activator.getDefault().getPinStore();
        String projectName = MetadataPinSupport.getProjectName(element);
        if (projectName == null)
        {
            return;
        }

        if (MetadataPinSupport.isProjectNode(element))
        {
            if (store.isProjectPinned(projectName))
            {
                store.unpinProject(projectName);
            }
            else
            {
                store.pinProject(projectName);
            }
            refreshUi();
            return;
        }

        PinTarget target = MetadataPinSupport.getPinTarget(element);
        if (target == null)
        {
            return;
        }
        if (store.isObjectEffectivelyPinned(projectName, MetadataPinSupport.getUuidPath(element)))
        {
            store.unpinObject(projectName, target.uuid());
        }
        else
        {
            store.pinObject(projectName, target);
        }
        refreshUi();
    }


    public static void unpinAllObjectsInProject(Object projectElement)
    {
        String projectName = MetadataPinSupport.getProjectName(projectElement);
        if (projectName == null)
        {
            return;
        }
        Activator.getDefault().getPinStore().unpinAllObjects(projectName);
        refreshUi();
    }


    public static void pinWithNested(Object element)
    {
        applyToSelfAndNested(element, true);
    }


    public static void unpinWithNested(Object element)
    {
        applyToSelfAndNested(element, false);
    }

    private static void applyToSelfAndNested(Object element, boolean pin)
    {
        PinStore store = Activator.getDefault().getPinStore();
        String projectName = MetadataPinSupport.getProjectName(element);
        PinTarget self = MetadataPinSupport.getPinTarget(element);
        if (projectName == null || self == null)
        {
            return;
        }

        List<PinTarget> selfAndNested = new ArrayList<>(MetadataPinSupport.getNestedPinTargets(element));
        selfAndNested.add(self);
        if (pin)
        {
            store.pinBranch(projectName, self, selfAndNested);
        }
        else
        {
            store.unpinBranch(projectName, self, selfAndNested);
        }
        refreshUi();
    }


    public static void refreshUi()
    {
        PlatformUI.getWorkbench().getDecoratorManager().update(PinDecorator.ID);
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        EditorPinToggleHandler.refreshElements(window);
        CommonViewer viewer = NavigatorAccess.findViewer(window).orElse(null);
        if (viewer != null && NavigatorAccess.isFilterActive(viewer, PinnedOnlyFilter.class))
        {
            Object[] expandedElements = viewer.getExpandedElements();
            viewer.refresh();
            viewer.setExpandedElements(expandedElements);
            viewer.getControl().getDisplay().asyncExec(() -> expandProjectsWithPinnedObjects(viewer));
        }
    }


    static void expandProjectsWithPinnedObjects(CommonViewer viewer)
    {
        if (viewer.getControl().isDisposed()
            || !NavigatorAccess.isFilterActive(viewer, PinnedOnlyFilter.class))
        {
            return;
        }
        PinStore store = Activator.getDefault().getPinStore();
        int pinnedObjects = 0;
        for (TreeItem item : viewer.getTree().getItems())
        {
            String projectName = MetadataPinSupport.getProjectName(item.getData());
            if (projectName != null)
            {
                pinnedObjects += store.getPinnedObjectCount(projectName);
                if (pinnedObjects > FavoriteUiLimits.MAX_AUTO_EXPANDED_OBJECTS)
                {
                    return;
                }
            }
        }
        for (TreeItem item : viewer.getTree().getItems())
        {
            Object element = item.getData();
            String projectName = MetadataPinSupport.getProjectName(element);
            if (projectName != null && store.hasPinnedObjects(projectName))
            {
                viewer.expandToLevel(element, EXPAND_LEVELS);
            }
        }
    }

}
