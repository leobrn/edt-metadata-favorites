/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.plugin.AbstractUIPlugin;


public class PinMenuContribution extends CompoundContributionItem
{
    private static final IContributionItem[] NO_ITEMS = new IContributionItem[0];


    private static final String TOGGLE_PIN_COMMAND_ID = "edt.metadata.favorites.togglePin";

    private static final ImageDescriptor PIN_ICON =
        AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/pin.png");

    private static final ImageDescriptor UNPIN_ICON =
        AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/unpin.png");

    private static final ImageDescriptor PIN_CHILDREN_ICON =
        AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/pin_children.png");

    private static final ImageDescriptor UNPIN_CHILDREN_ICON =
        AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/unpin_children.png");

    @Override
    protected IContributionItem[] getContributionItems()
    {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null)
        {
            return NO_ITEMS;
        }

        ISelection selection = window.getSelectionService().getSelection();
        if (!(selection instanceof IStructuredSelection structured) || structured.size() != 1)
        {
            return NO_ITEMS;
        }

        Object element = structured.getFirstElement();
        PinStore store = Activator.getDefault().getPinStore();
        List<IContributionItem> items = new ArrayList<>();

        if (MetadataPinSupport.isProjectNode(element))
        {
            addProjectAction(items, store, element);
        }
        else
        {
            addObjectActions(items, store, element);
        }

        if (items.isEmpty())
        {
            return NO_ITEMS;
        }

        items.add(0, new Separator());
        items.add(new Separator());
        return items.toArray(NO_ITEMS);
    }

    private void addProjectAction(List<IContributionItem> items, PinStore store, Object element)
    {
        String projectName = MetadataPinSupport.getProjectName(element);
        if (projectName == null)
        {
            return;
        }

        boolean pinned = store.isProjectPinned(projectName);
        Action action = new Action((pinned ? "Удалить проект из избранного" : "Добавить проект в избранное")
            + acceleratorSuffix())
        {
            @Override
            public void run()
            {
                PinOperations.togglePin(element);
            }
        };
        action.setImageDescriptor(pinned ? UNPIN_ICON : PIN_ICON);
        items.add(new ActionContributionItem(action));

        if (store.hasPinnedObjects(projectName))
        {
            Action unpinAllAction = new Action("Удалить все объекты из избранного")
            {
                @Override
                public void run()
                {
                    PinOperations.unpinAllObjectsInProject(element);
                }
            };
            unpinAllAction.setImageDescriptor(UNPIN_CHILDREN_ICON);
            items.add(new ActionContributionItem(unpinAllAction));
        }
    }

    private void addObjectActions(List<IContributionItem> items, PinStore store, Object element)
    {
        String projectName = MetadataPinSupport.getProjectName(element);
        PinTarget self = MetadataPinSupport.getPinTarget(element);
        if (projectName == null || self == null)
        {
            return;
        }

        boolean pinned =
            store.isObjectEffectivelyPinned(projectName, MetadataPinSupport.getUuidPath(element));
        Action toggleAction = new Action((pinned ? "Удалить из избранного" : "Добавить в избранное")
            + acceleratorSuffix())
        {
            @Override
            public void run()
            {
                PinOperations.togglePin(element);
            }
        };
        toggleAction.setImageDescriptor(pinned ? UNPIN_ICON : PIN_ICON);
        items.add(new ActionContributionItem(toggleAction));

        List<PinTarget> nested = MetadataPinSupport.getNestedPinTargets(element);
        if (nested.isEmpty())
        {
            return;
        }

        List<PinTarget> selfAndNested = new ArrayList<>(nested);
        selfAndNested.add(self);

        boolean anyPinned = false;
        for (PinTarget candidate : selfAndNested)
        {
            if (store.isObjectPinned(projectName, candidate.uuid()))
            {
                anyPinned = true;
            }
        }

        if (!store.isRecursivePin(projectName, self.uuid()))
        {
            Action pinNestedAction = new Action("Добавить в избранное с вложенными")
            {
                @Override
                public void run()
                {
                    PinOperations.pinWithNested(element);
                }
            };
            pinNestedAction.setImageDescriptor(PIN_CHILDREN_ICON);
            items.add(new ActionContributionItem(pinNestedAction));
        }

        if (pinned || anyPinned || store.isRecursivePin(projectName, self.uuid()))
        {
            Action unpinNestedAction = new Action("Удалить из избранного с вложенными")
            {
                @Override
                public void run()
                {
                    PinOperations.unpinWithNested(element);
                }
            };
            unpinNestedAction.setImageDescriptor(UNPIN_CHILDREN_ICON);
            items.add(new ActionContributionItem(unpinNestedAction));
        }
    }


    private static String acceleratorSuffix()
    {
        IBindingService bindingService = PlatformUI.getWorkbench().getService(IBindingService.class);
        if (bindingService == null)
        {
            return "";
        }
        TriggerSequence sequence = bindingService.getBestActiveBindingFor(TOGGLE_PIN_COMMAND_ID);
        return sequence == null ? "" : "\t" + sequence.format();
    }
}
