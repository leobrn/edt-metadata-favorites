/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.navigator.ICommonFilterDescriptor;
import org.eclipse.ui.navigator.INavigatorFilterService;


public class ToggleFilterHandler extends AbstractHandler
    implements IElementUpdater
{
    public static final String COMMAND_ID = "edt.metadata.favorites.toggleFilter";

    private static final String NAVIGATOR_ID = "com._1c.g5.v8.dt.ui2.navigator";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        updateFilter(window, null);

        return null;
    }

    static void enableFilter(IWorkbenchWindow window)
    {
        updateFilter(window, Boolean.TRUE);
    }

    private static void updateFilter(IWorkbenchWindow window, Boolean requestedState)
    {
        CommonViewer viewer = findViewer(window);
        if (viewer == null)
        {
            return;
        }
        INavigatorFilterService filterService = viewer.getNavigatorContentService().getFilterService();
        Set<String> activeIds = activeFilterIds(filterService);
        boolean currentlyActive = activeIds.contains(PinnedOnlyFilter.ID);
        boolean enabling = requestedState == null ? !currentlyActive : requestedState;
        if (currentlyActive == enabling)
        {
            return;
        }
        if (enabling)
        {
            activeIds.add(PinnedOnlyFilter.ID);
        }
        else
        {
            activeIds.remove(PinnedOnlyFilter.ID);
        }

        filterService.activateFilterIdsAndUpdateViewer(activeIds.toArray(String[]::new));
        filterService.persistFilterActivationState();
        if (enabling)
        {
            viewer.getControl().getDisplay().asyncExec(
                () -> PinOperations.expandProjectsWithPinnedObjects(viewer));
        }
        refreshCommand(window);
    }

    private static Set<String> activeFilterIds(INavigatorFilterService filterService)
    {
        Set<String> result = new LinkedHashSet<>();
        for (ICommonFilterDescriptor descriptor : filterService.getVisibleFilterDescriptors())
        {
            if (filterService.isActive(descriptor.getId()))
            {
                result.add(descriptor.getId());
            }
        }
        return result;
    }

    private static CommonViewer findViewer(IWorkbenchWindow window)
    {
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
        return view instanceof CommonNavigator navigator ? navigator.getCommonViewer() : null;
    }

    private static void refreshCommand(IWorkbenchWindow window)
    {
        ICommandService commandService = window.getService(ICommandService.class);
        if (commandService != null)
        {
            commandService.refreshElements(COMMAND_ID, null);
        }
    }

    private static boolean isFilterActive(CommonViewer viewer)
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

    @Override
    public void updateElement(UIElement element, Map parameters)
    {
        try
        {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null)
            {
                return;
            }
            IWorkbenchPage page = window.getActivePage();
            if (page == null)
            {
                return;
            }
            IViewPart view = page.findView(NAVIGATOR_ID);
            if (!(view instanceof CommonNavigator navigator))
            {
                return;
            }
            CommonViewer viewer = navigator.getCommonViewer();
            element.setChecked(isFilterActive(viewer));
        }
        catch (RuntimeException e)
        {
        }
    }
}
