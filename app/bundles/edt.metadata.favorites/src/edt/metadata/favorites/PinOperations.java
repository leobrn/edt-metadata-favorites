/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;


public final class PinOperations
{
    private static final String NAVIGATOR_ID = "com._1c.g5.v8.dt.ui2.navigator";


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
        CommonNavigator navigator = findNavigator();
        if (navigator != null)
        {
            CommonViewer viewer = navigator.getCommonViewer();
            Object[] expandedElements = viewer.getExpandedElements();
            viewer.refresh();
            viewer.setExpandedElements(expandedElements);
            viewer.getControl().getDisplay().asyncExec(() -> expandProjectsWithPinnedObjects(viewer));
        }
    }


    static void expandProjectsWithPinnedObjects(CommonViewer viewer)
    {
        if (viewer.getControl().isDisposed() || !isPinnedFilterActive(viewer))
        {
            return;
        }
        PinStore store = Activator.getDefault().getPinStore();
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

    private static boolean isPinnedFilterActive(CommonViewer viewer)
    {
        for (ViewerFilter filter : viewer.getFilters())
        {
            if (filter instanceof PinnedOnlyFilter)
            {
                return true;
            }
        }
        return false;
    }

    static CommonNavigator findNavigator()
    {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null)
        {
            return null;
        }
        IWorkbenchPage page = window.getActivePage();
        if (page == null)
        {
            return null;
        }
        IViewPart view = page.findView(NAVIGATOR_ID);
        return view instanceof CommonNavigator navigator ? navigator : null;
    }
}
