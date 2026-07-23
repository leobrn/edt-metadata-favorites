/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.plugin.AbstractUIPlugin;


public class PinDecorator extends LabelProvider
    implements ILightweightLabelDecorator
{
    public static final String ID = "edt.metadata.favorites.decorator";


    private static final ImageDescriptor PIN_OVERLAY =
        AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/lock_ovr.png");

    @Override
    public void decorate(Object element, IDecoration decoration)
    {
        PinStore store = Activator.getDefault().getPinStore();
        String projectName = MetadataPinSupport.getProjectName(element);
        if (projectName == null)
        {
            return;
        }

        if (MetadataPinSupport.isProjectNode(element))
        {
            if (store.isProjectPinned(projectName))
            {
                decoration.addOverlay(PIN_OVERLAY, IDecoration.TOP_RIGHT);
            }
            return;
        }

        String uuid = MetadataPinSupport.getUuid(element);
        if (uuid != null
            && store.isObjectEffectivelyPinned(projectName, MetadataPinSupport.getUuidPath(element)))
        {
            decoration.addOverlay(PIN_OVERLAY, IDecoration.TOP_RIGHT);
        }
    }
}
