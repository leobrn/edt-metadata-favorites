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
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;


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

        String defaultProject = defaultProjectName(window, projectNames);
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


    private static String defaultProjectName(IWorkbenchWindow window, List<String> projectNames)
    {
        ISelection selection = window.getSelectionService().getSelection();
        if (selection instanceof IStructuredSelection structured && !structured.isEmpty())
        {
            String projectName = MetadataPinSupport.getProjectName(structured.getFirstElement());
            if (projectName != null && projectNames.contains(projectName))
            {
                return projectName;
            }
        }
        return projectNames.get(0);
    }
}
