/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.navigator.ICommonFilterDescriptor;
import org.eclipse.ui.navigator.INavigatorFilterService;


final class NavigatorFilterToggle
{
    private NavigatorFilterToggle()
    {
    }

    static void update(IWorkbenchWindow window, String filterId, String commandId,
        Boolean requestedState, Consumer<CommonViewer> enabledAction)
    {
        CommonViewer viewer = NavigatorAccess.findViewer(window).orElse(null);
        update(window, viewer, filterId, commandId, requestedState, enabledAction);
    }

    static void update(IWorkbenchWindow window, CommonViewer viewer, String filterId,
        String commandId, Boolean requestedState, Consumer<CommonViewer> enabledAction)
    {
        if (viewer == null)
        {
            return;
        }

        INavigatorFilterService filterService = viewer.getNavigatorContentService().getFilterService();
        Set<String> activeIds = activeFilterIds(filterService);
        boolean currentlyActive = activeIds.contains(filterId);
        boolean enabling = requestedState == null ? !currentlyActive : requestedState;
        if (currentlyActive == enabling)
        {
            return;
        }
        if (enabling)
        {
            activeIds.add(filterId);
        }
        else
        {
            activeIds.remove(filterId);
        }

        filterService.activateFilterIdsAndUpdateViewer(activeIds.toArray(String[]::new));
        filterService.persistFilterActivationState();
        if (enabling && enabledAction != null)
        {
            enabledAction.accept(viewer);
        }
        refreshCommand(window, commandId);
    }

    static boolean isActive(CommonViewer viewer, String filterId)
    {
        INavigatorFilterService filterService = viewer.getNavigatorContentService().getFilterService();
        return activeFilterIds(filterService).contains(filterId);
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

    private static void refreshCommand(IWorkbenchWindow window, String commandId)
    {
        ICommandService commandService = window.getService(ICommandService.class);
        if (commandService != null)
        {
            commandService.refreshElements(commandId, null);
        }
    }
}
