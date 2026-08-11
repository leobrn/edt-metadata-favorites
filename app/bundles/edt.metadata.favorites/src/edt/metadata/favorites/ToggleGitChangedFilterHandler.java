/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.navigator.CommonViewer;


public class ToggleGitChangedFilterHandler
    extends AbstractHandler
    implements IElementUpdater
{
    public static final String COMMAND_ID = "edt.metadata.favorites.toggleGitChangedFilter";

    private boolean updateElementFailureLogged;

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        CommonViewer viewer = NavigatorAccess.findViewer(window).orElse(null);
        boolean disabling = viewer != null
            && NavigatorFilterToggle.isActive(viewer, GitChangedOnlyFilter.ID);
        Object[] roots = viewer == null ? new Object[0] : GitChangedAutoExpand.rootElements(viewer);
        if (viewer != null && !disabling)
        {
            Activator.getDefault().getGitChangeCache().invalidateForFilterActivation();
        }
        NavigatorFilterToggle.update(window, viewer, GitChangedOnlyFilter.ID, COMMAND_ID, null,
            enabledViewer -> GitChangedAutoExpand.request(enabledViewer, roots));
        if (disabling)
        {
            GitChangedAutoExpand.cancel(viewer);
        }
        return null;
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
            if (viewer != null)
            {
                element.setChecked(
                    NavigatorAccess.isFilterActive(viewer, GitChangedOnlyFilter.class));
            }
        }
        catch (RuntimeException e)
        {
            if (!updateElementFailureLogged)
            {
                updateElementFailureLogged = true;
                Activator.logError(
                    "Не удалось обновить состояние команды " + COMMAND_ID, e);
            }
        }
    }
}
