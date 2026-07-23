/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

import com._1c.g5.v8.dt.ui.editor.IDtEditor;


public class PinToggleHandler extends AbstractHandler
{
    @Override
    public Object execute(ExecutionEvent event)
    {
        IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        if (activePart instanceof IDtEditor<?> editor)
        {
            PinOperations.togglePin(editor.getModel());
            return null;
        }

        IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
        if (selection.size() == 1)
        {
            PinOperations.togglePin(selection.getFirstElement());
        }
        return null;
    }
}
