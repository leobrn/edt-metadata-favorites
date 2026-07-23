/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com._1c.g5.v8.dt.ui.editor.IDtEditor;


public class EditorPinToggleHandler extends AbstractHandler
    implements IElementUpdater
{
    public static final String COMMAND_ID = "edt.metadata.favorites.toggleEditorPin";

    private static final String PIN_ICON = "icons/pin.png";

    private static final String UNPIN_ICON = "icons/unpin.png";

    private static final String PIN_TOOLTIP = "Добавить открытый объект в избранное";

    private static final String UNPIN_TOOLTIP = "Удалить открытый объект из избранного";

    private final IPartListener2 partListener = new IPartListener2()
    {
        @Override
        public void partActivated(IWorkbenchPartReference partRef)
        {
            refreshElements(observedWindow);
        }
    };

    private IWorkbenchWindow observedWindow;

    public EditorPinToggleHandler()
    {
        observedWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (observedWindow != null)
        {
            observedWindow.getPartService().addPartListener(partListener);
        }
    }

    @Override
    public Object execute(ExecutionEvent event)
    {
        Object model = getActiveEditorModel();
        if (model != null)
        {
            PinOperations.togglePin(model);
        }
        return null;
    }

    @Override
    public void updateElement(UIElement element, Map parameters)
    {
        try
        {
            boolean pinned = isPinned(getActiveEditorModel());
            element.setIcon(AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID,
                pinned ? UNPIN_ICON : PIN_ICON));
            element.setTooltip(pinned ? UNPIN_TOOLTIP : PIN_TOOLTIP);
        }
        catch (RuntimeException e)
        {
        }
    }

    @Override
    public void dispose()
    {
        if (observedWindow != null)
        {
            observedWindow.getPartService().removePartListener(partListener);
            observedWindow = null;
        }
        super.dispose();
    }

    static void refreshElements(IWorkbenchWindow window)
    {
        if (window == null)
        {
            return;
        }
        ICommandService commandService = window.getService(ICommandService.class);
        if (commandService != null)
        {
            commandService.refreshElements(COMMAND_ID, null);
        }
    }

    private static Object getActiveEditorModel()
    {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null || window.getActivePage() == null)
        {
            return null;
        }
        IEditorPart editorPart = window.getActivePage().getActiveEditor();
        return editorPart instanceof IDtEditor<?> editor ? editor.getModel() : null;
    }

    private static boolean isPinned(Object model)
    {
        if (model == null)
        {
            return false;
        }
        String projectName = MetadataPinSupport.getProjectName(model);
        PinTarget target = MetadataPinSupport.getPinTarget(model);
        return projectName != null && target != null
            && Activator.getDefault().getPinStore().isObjectEffectivelyPinned(projectName,
                MetadataPinSupport.getUuidPath(model));
    }
}
