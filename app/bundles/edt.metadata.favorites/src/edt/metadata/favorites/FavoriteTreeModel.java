/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdClassPackage;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;


final class FavoriteTreeModel
{
    private static final ContainmentReachability CONTAINMENT_REACHABILITY =
        new ContainmentReachability();

    private static final Map<String, String> NESTED_GROUP_LABELS = Map.ofEntries(
        Map.entry("attributes", "Реквизиты"),
        Map.entry("tabularSections", "Табличные части"),
        Map.entry("forms", "Формы"),
        Map.entry("commands", "Команды"),
        Map.entry("templates", "Макеты"),
        Map.entry("dimensions", "Измерения"),
        Map.entry("resources", "Ресурсы"),
        Map.entry("recalculations", "Перерасчёты"),
        Map.entry("enumValues", "Значения"),
        Map.entry("columns", "Графы"),
        Map.entry("addressingAttributes", "Реквизиты адресации"),
        Map.entry("operations", "Операции"),
        Map.entry("fields", "Поля"),
        Map.entry("parameters", "Параметры"));

    static final String COMMON_GROUP = "Общие";


    private static final Set<String> COMMON_KINDS = Set.of(
        "Subsystem", "CommonModule", "SessionParameter", "Role", "CommonAttribute", "ExchangePlan",
        "FilterCriterion", "EventSubscription", "ScheduledJob", "Bot", "FunctionalOption",
        "FunctionalOptionsParameter", "DefinedType", "SettingsStorage", "CommonForm", "CommonCommand",
        "CommandGroup", "CommonTemplate", "CommonPicture", "XDTOPackage", "WebService", "HTTPService",
        "WSReference", "WebSocketClient", "IntegrationService", "PaletteColor", "StyleItem", "Style",
        "Language", "Interface");


    private static final List<String> COMMON_GROUP_ORDER = List.of(
        "Подсистемы", "Общие модули", "Параметры сеанса", "Роли", "Общие реквизиты", "Планы обмена",
        "Критерии отбора", "Подписки на события", "Регламентные задания", "Боты", "Функциональные опции",
        "Параметры функциональных опций", "Определяемые типы", "Хранилища настроек", "Общие формы",
        "Общие команды", "Группы команд", "Общие макеты", "Общие картинки", "XDTO-пакеты", "Web-сервисы",
        "HTTP-сервисы", "WS-ссылки", "WebSocket-клиенты", "Сервисы интеграции", "Цвета палитры",
        "Элементы стиля", "Стили", "Языки", "Интерфейсы");

    private FavoriteTreeModel()
    {
    }


    record BuildResult(List<FavoriteTreeNode> roots)
    {
    }


    static BuildResult build(Configuration configuration)
    {
        Map<String, MdObject> objectsByUuid = new LinkedHashMap<>();
        for (EReference reference : configuration.eClass().getEAllReferences())
        {
            if (!reference.isMany()
                || !MdClassPackage.Literals.MD_OBJECT.isSuperTypeOf(reference.getEReferenceType()))
            {
                continue;
            }

            Object value = configuration.eGet(reference, false);
            if (!(value instanceof Iterable<?> values))
            {
                continue;
            }
            for (Object candidate : values)
            {
                if (candidate instanceof EObject eObject)
                {
                    EObject resolved = resolve(eObject, configuration);
                    if (!(resolved instanceof MdObject mdObject))
                    {
                        continue;
                    }
                    String uuid = MetadataPinSupport.getUuid(mdObject);
                    if (uuid != null)
                    {
                        objectsByUuid.putIfAbsent(uuid, mdObject);
                    }
                }
            }
        }

        Map<String, FavoriteTreeNode> roots = new LinkedHashMap<>();
        Map<String, FavoriteTreeNode> commonGroups = new LinkedHashMap<>();
        for (MdObject mdObject : objectsByUuid.values())
        {
            String uuid = MetadataPinSupport.getUuid(mdObject);
            String kind = mdObject.eClass().getName();
            String groupLabel = MetadataTypeNames.groupLabel(kind);
            FavoriteTreeNode group;
            if (COMMON_KINDS.contains(kind))
            {
                FavoriteTreeNode commonRoot = roots.computeIfAbsent(COMMON_GROUP, FavoriteTreeNode::group);
                group = commonGroups.computeIfAbsent(groupLabel, label -> {
                    FavoriteTreeNode child = FavoriteTreeNode.group(label);
                    commonRoot.addChild(child);
                    return child;
                });
            }
            else
            {
                group = roots.computeIfAbsent(groupLabel, FavoriteTreeNode::group);
            }
            String fqn = MetadataPinSupport.getFqn(mdObject);
            String label = mdObject.getName() != null ? mdObject.getName() : fqn;
            FavoriteTreeNode objectNode = FavoriteTreeNode.object(label, new PinTarget(uuid, fqn), mdObject);
            group.addChild(objectNode);
            appendNestedObjects(objectNode, mdObject, CONTAINMENT_REACHABILITY);
        }

        List<FavoriteTreeNode> result = new ArrayList<>(roots.values());
        for (FavoriteTreeNode root : result)
        {
            if (COMMON_GROUP.equals(root.label))
            {
                root.children.sort(Comparator.comparingInt(
                    node -> commonGroupOrder(node.label)));
            }
            root.sortLeafChildren();
        }
        return new BuildResult(result);
    }


    private static void appendNestedObjects(FavoriteTreeNode rootNode, MdObject rootObject,
        ContainmentReachability reachability)
    {
        Map<FavoriteTreeNode, Map<String, FavoriteTreeNode>> groups = new IdentityHashMap<>();
        Set<EObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendContainedObjects(rootObject, rootNode, null, groups, visited, reachability);
    }

    private static void appendContainedObjects(EObject owner, FavoriteTreeNode parentNode,
        String inheritedGroupKey, Map<FavoriteTreeNode, Map<String, FavoriteTreeNode>> groups,
        Set<EObject> visited, ContainmentReachability reachability)
    {
        if (!visited.add(owner))
        {
            return;
        }
        for (EReference reference : owner.eClass().getEAllContainments())
        {
            if (!reachability.mayContainMdObject(reference.getEReferenceType()))
            {
                continue;
            }
            Object value = owner.eGet(reference, false);
            if (reference.isMany() && value instanceof Iterable<?> values)
            {
                for (Object candidate : values)
                {
                    if (candidate instanceof EObject child)
                    {
                        appendContainedObject(resolve(child, owner), parentNode,
                            inheritedGroupKey == null ? reference.getName() : inheritedGroupKey,
                            groups, visited, reachability);
                    }
                }
            }
            else if (value instanceof EObject child)
            {
                appendContainedObject(resolve(child, owner), parentNode,
                    inheritedGroupKey == null ? reference.getName() : inheritedGroupKey,
                    groups, visited, reachability);
            }
        }
    }

    private static void appendContainedObject(EObject child, FavoriteTreeNode parentNode, String groupKey,
        Map<FavoriteTreeNode, Map<String, FavoriteTreeNode>> groups, Set<EObject> visited,
        ContainmentReachability reachability)
    {
        if (child instanceof MdObject mdObject)
        {
            String uuid = MetadataPinSupport.getUuid(mdObject);
            if (uuid == null)
            {
                appendContainedObjects(child, parentNode, groupKey, groups, visited, reachability);
                return;
            }
            String groupLabel = NESTED_GROUP_LABELS.getOrDefault(groupKey, groupKey);
            FavoriteTreeNode nestedGroup = groups.computeIfAbsent(parentNode, key -> new LinkedHashMap<>())
                .computeIfAbsent(groupKey, key -> {
                    FavoriteTreeNode value = FavoriteTreeNode.group(groupLabel);
                    parentNode.addChild(value);
                    return value;
                });

            String fqn = nestedFqn(parentNode, mdObject);
            String label = mdObject.getName() != null ? mdObject.getName() : fqn;
            FavoriteTreeNode childNode = FavoriteTreeNode.object(label, new PinTarget(uuid, fqn), mdObject);
            nestedGroup.addChild(childNode);
            appendContainedObjects(child, childNode, null, groups, visited, reachability);
        }
        else
        {
            appendContainedObjects(child, parentNode, groupKey, groups, visited, reachability);
        }
    }

    static String nestedFqn(FavoriteTreeNode parentNode, MdObject mdObject)
    {
        String ownFqn = mdObject.eClass().getName() + "." + mdObject.getName();
        String parentFqn = parentNode.target == null ? null : parentNode.target.fqn();
        return parentFqn == null || parentFqn.isEmpty() ? ownFqn : parentFqn + "." + ownFqn;
    }

    private static EObject resolve(EObject object, EObject context)
    {
        return object.eIsProxy() ? EcoreUtil.resolve(object, context) : object;
    }

    private static final class ContainmentReachability
    {
        private final Map<EClass, Boolean> cache = new IdentityHashMap<>();

        synchronized boolean mayContainMdObject(EClass type)
        {
            Boolean cached = cache.get(type);
            if (cached != null)
            {
                return cached;
            }

            Set<EClass> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            ArrayDeque<EClass> pending = new ArrayDeque<>();
            pending.add(type);
            while (!pending.isEmpty())
            {
                EClass current = pending.removeFirst();
                if (!visited.add(current))
                {
                    continue;
                }
                if (MdClassPackage.Literals.MD_OBJECT.isSuperTypeOf(current))
                {
                    cache.put(type, true);
                    return true;
                }
                for (EReference reference : current.getEAllContainments())
                {
                    pending.add(reference.getEReferenceType());
                }
                addKnownSubtypes(current, pending);
            }
            cache.put(type, false);
            return false;
        }

        private static void addKnownSubtypes(EClass type, ArrayDeque<EClass> pending)
        {
            EPackage ePackage = type.getEPackage();
            if (ePackage == null)
            {
                return;
            }
            for (EClassifier classifier : ePackage.getEClassifiers())
            {
                if (classifier instanceof EClass candidate && candidate != type
                    && type.isSuperTypeOf(candidate))
                {
                    pending.add(candidate);
                }
            }
        }
    }

    private static int commonGroupOrder(String label)
    {
        int index = COMMON_GROUP_ORDER.indexOf(label);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}
