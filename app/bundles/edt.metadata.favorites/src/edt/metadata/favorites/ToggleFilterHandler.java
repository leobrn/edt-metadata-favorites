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
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.navigator.ICommonFilterDescriptor;
import org.eclipse.ui.navigator.INavigatorFilterService;


public class ToggleFilterHandler extends AbstractHandler
    implements IElementUpdater
{
    public static final String COMMAND_ID = "edt.metadata.favorites.toggleFilter";

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
        CommonViewer viewer = NavigatorAccess.findViewer(window).orElse(null);
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

    private static void refreshCommand(IWorkbenchWindow window)
    {
        ICommandService commandService = window.getService(ICommandService.class);
        if (commandService != null)
        {
            commandService.refreshElements(COMMAND_ID, null);
        }
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
            CommonViewer viewer = NavigatorAccess.findViewer(window).orElse(null);
            if (viewer == null)
            {
                return;
            }
            element.setChecked(NavigatorAccess.isFilterActive(viewer, PinnedOnlyFilter.class));
        }
        catch (RuntimeException e)
        {
        }
    }
}
