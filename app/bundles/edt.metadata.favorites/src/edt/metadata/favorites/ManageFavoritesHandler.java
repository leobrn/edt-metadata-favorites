/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;

import com._1c.g5.v8.dt.ui.editor.IDtEditor;


public class ManageFavoritesHandler extends AbstractHandler
{
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window == null)
        {
            return null;
        }

        List<String> projectNames = openWorkspaceProjectNames();
        if (projectNames.isEmpty())
        {
            return null;
        }

        String defaultProject = defaultProjectName(window, HandlerUtil.getActivePart(event), projectNames);
        FavoritesManagementDialog dialog =
            new FavoritesManagementDialog(window.getShell(), projectNames, defaultProject);
        dialog.open();
        return null;
    }


    private static List<String> openWorkspaceProjectNames()
    {
        List<String> names = new ArrayList<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects())
        {
            if (project.isOpen() && MetadataPinSupport.getConfiguration(project) != null)
            {
                names.add(project.getName());
            }
        }
        names.sort(String::compareTo);
        return names;
    }


    private static String defaultProjectName(IWorkbenchWindow window, IWorkbenchPart activePart,
        List<String> projectNames)
    {
        String projectName = projectNameFromActivePart(activePart, projectNames);
        if (projectName == null)
        {
            projectName = projectNameFromActiveEditor(window, projectNames);
        }
        if (projectName == null)
        {
            projectName = projectNameFromNavigator(window, projectNames);
        }
        if (projectName == null)
        {
            projectName = projectNameFromSelection(window.getSelectionService().getSelection(), projectNames);
        }
        return projectName == null ? projectWithFavorites(projectNames) : projectName;
    }


    /**
     * Запасной выбор, когда проект не определяется по активной части, редактору или выделению.
     * Первый по алфавиту проект чаще всего не тот, с которым работает пользователь, поэтому
     * предпочитается проект, у которого уже есть избранное.
     */
    private static String projectWithFavorites(List<String> projectNames)
    {
        PinStore store = Activator.getDefault().getPinStore();
        for (String projectName : projectNames)
        {
            if (store.isProjectPinned(projectName) || store.hasPinnedObjects(projectName))
            {
                return projectName;
            }
        }
        return projectNames.get(0);
    }


    private static String projectNameFromActivePart(IWorkbenchPart activePart, List<String> projectNames)
    {
        if (activePart instanceof IDtEditor<?> editor)
        {
            return acceptProjectName(MetadataPinSupport.getProjectName(editor.getModel()), projectNames);
        }
        if (activePart instanceof CommonNavigator navigator && isNavigatorPart(activePart))
        {
            return projectNameFromViewer(navigator.getCommonViewer(), projectNames);
        }
        return null;
    }


    private static boolean isNavigatorPart(IWorkbenchPart part)
    {
        IWorkbenchPartSite site = part.getSite();
        return site != null && NavigatorAccess.NAVIGATOR_ID.equals(site.getId());
    }


    private static String projectNameFromNavigator(IWorkbenchWindow window, List<String> projectNames)
    {
        String projectName = projectNameFromViewer(NavigatorAccess.findViewer(window).orElse(null), projectNames);
        if (projectName != null)
        {
            return projectName;
        }
        return projectNameFromSelection(window.getSelectionService().getSelection(NavigatorAccess.NAVIGATOR_ID),
            projectNames);
    }


    private static String projectNameFromViewer(CommonViewer viewer, List<String> projectNames)
    {
        if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed())
        {
            return null;
        }
        return projectNameFromSelection(viewer.getStructuredSelection(), projectNames);
    }


    private static String projectNameFromActiveEditor(IWorkbenchWindow window, List<String> projectNames)
    {
        IWorkbenchPage page = window.getActivePage();
        if (page == null)
        {
            return null;
        }
        IEditorPart editor = page.getActiveEditor();
        if (!(editor instanceof IDtEditor<?> dtEditor))
        {
            return null;
        }
        return acceptProjectName(MetadataPinSupport.getProjectName(dtEditor.getModel()), projectNames);
    }


    private static String projectNameFromSelection(ISelection selection, List<String> projectNames)
    {
        if (selection instanceof IStructuredSelection structured && !structured.isEmpty())
        {
            return acceptProjectName(MetadataPinSupport.getProjectName(structured.getFirstElement()), projectNames);
        }
        return null;
    }


    private static String acceptProjectName(String projectName, List<String> projectNames)
    {
        return projectName != null && projectNames.contains(projectName) ? projectName : null;
    }
}
