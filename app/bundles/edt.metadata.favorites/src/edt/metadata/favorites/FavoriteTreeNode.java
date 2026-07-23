/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com._1c.g5.v8.dt.metadata.mdclass.MdObject;


final class FavoriteTreeNode
{
    final String label;


    final String normalizedLabel;

    final String normalizedFqn;


    final PinTarget target;


    final MdObject mdObject;


    final List<String> uuidPath;

    final List<FavoriteTreeNode> children = new ArrayList<>();

    private FavoriteTreeNode(String label, PinTarget target, MdObject mdObject)
    {
        this.label = label;
        this.normalizedLabel = normalize(label);
        this.normalizedFqn = normalize(target == null ? null : target.fqn());
        this.target = target;
        this.mdObject = mdObject;
        this.uuidPath = mdObject == null ? List.of() : MetadataPinSupport.getUuidPath(mdObject);
    }

    static FavoriteTreeNode group(String label)
    {
        return new FavoriteTreeNode(label, null, null);
    }

    static FavoriteTreeNode object(String label, PinTarget target, MdObject mdObject)
    {
        return new FavoriteTreeNode(label, target, mdObject);
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    boolean isGroup()
    {
        return target == null;
    }

    boolean isObject()
    {
        return target != null;
    }

    void forEachObject(Consumer<FavoriteTreeNode> action)
    {
        if (isObject())
        {
            action.accept(this);
        }
        children.forEach(child -> child.forEachObject(action));
    }

    MdObject firstMdObject()
    {
        if (mdObject != null)
        {
            return mdObject;
        }
        for (FavoriteTreeNode child : children)
        {
            MdObject result = child.firstMdObject();
            if (result != null)
            {
                return result;
            }
        }
        return null;
    }

    void sortLeafChildren()
    {
        if (children.stream().allMatch(FavoriteTreeNode::isObject))
        {
            children.sort(Comparator.comparing(node -> node.label));
        }
        children.forEach(FavoriteTreeNode::sortLeafChildren);
    }
}
