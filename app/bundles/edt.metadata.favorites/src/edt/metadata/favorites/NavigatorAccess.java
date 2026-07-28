/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.Optional;

import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;


final class NavigatorAccess
{
    static final String NAVIGATOR_ID = "com._1c.g5.v8.dt.ui2.navigator";

    private NavigatorAccess()
    {
    }

    static Optional<CommonNavigator> findNavigator(IWorkbenchWindow window)
    {
        if (window == null)
        {
            return Optional.empty();
        }
        IWorkbenchPage page = window.getActivePage();
        if (page == null)
        {
            return Optional.empty();
        }
        IViewPart view = page.findView(NAVIGATOR_ID);
        return view instanceof CommonNavigator navigator ? Optional.of(navigator) : Optional.empty();
    }

    static Optional<CommonViewer> findViewer(IWorkbenchWindow window)
    {
        return findNavigator(window).map(CommonNavigator::getCommonViewer);
    }

    static boolean isFilterActive(CommonViewer viewer, Class<? extends ViewerFilter> filterType)
    {
        for (ViewerFilter filter : viewer.getFilters())
        {
            if (filterType.isInstance(filter))
            {
                return true;
            }
        }
        return false;
    }
}
