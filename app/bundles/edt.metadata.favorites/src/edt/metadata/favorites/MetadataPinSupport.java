/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EObject;

import com._1c.g5.v8.dt.core.platform.IConfigurationProvider;
import com._1c.g5.v8.dt.core.platform.IResourceLookup;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;
import com._1c.g5.wiring.ServiceAccess;


public final class MetadataPinSupport
{
    private MetadataPinSupport()
    {
    }


    public static boolean isProjectNode(Object element)
    {
        return element instanceof IProject;
    }


    public static String getProjectName(Object element)
    {
        if (element instanceof IProject project)
        {
            return project.getName();
        }

        if (element instanceof EObject eObject)
        {
            try
            {
                IResourceLookup resourceLookup = ServiceAccess.get(IResourceLookup.class);
                IProject project = resourceLookup.getProject(eObject);
                return project == null ? null : project.getName();
            }
            catch (RuntimeException e)
            {
                return null;
            }
        }

        return null;
    }


    public static String getUuid(Object element)
    {
        if (!(element instanceof MdObject mdObject))
        {
            return null;
        }
        java.util.UUID uuid = mdObject.getUuid();
        return uuid == null ? null : uuid.toString();
    }


    public static String getFqn(Object element)
    {
        if (!(element instanceof MdObject mdObject))
        {
            return null;
        }

        String ownFqn = mdObject.eClass().getName() + "." + mdObject.getName();
        String parentFqn = getFqn(mdObject.eContainer());
        return parentFqn == null ? ownFqn : parentFqn + "." + ownFqn;
    }


    public static PinTarget getPinTarget(Object element)
    {
        String uuid = getUuid(element);
        if (uuid == null)
        {
            return null;
        }
        return new PinTarget(uuid, getFqn(element));
    }


    public static java.util.List<String> getUuidPath(Object element)
    {
        java.util.List<String> result = new java.util.ArrayList<>();
        EObject current = element instanceof EObject eObject ? eObject : null;
        while (current != null)
        {
            String uuid = getUuid(current);
            if (uuid != null)
            {
                result.add(uuid);
            }
            current = current.eContainer();
        }
        return result;
    }


    public static java.util.List<PinTarget> getNestedPinTargets(Object element)
    {
        java.util.List<PinTarget> result = new java.util.ArrayList<>();
        if (!(element instanceof MdObject mdObject))
        {
            return result;
        }

        java.util.Iterator<EObject> iterator = mdObject.eAllContents();
        while (iterator.hasNext())
        {
            EObject child = iterator.next();
            String uuid = getUuid(child);
            if (uuid != null)
            {
                result.add(new PinTarget(uuid, getFqn(child)));
            }
        }
        return result;
    }


    public static Configuration getConfiguration(IProject project)
    {
        try
        {
            IConfigurationProvider provider = ServiceAccess.get(IConfigurationProvider.class);
            return provider.getConfiguration(project);
        }
        catch (RuntimeException e)
        {
            Activator.logError("Не удалось получить конфигурацию проекта " + project.getName(), e);
            return null;
        }
    }
}
