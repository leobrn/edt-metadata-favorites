/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.Test;


public class GitChangeSnapshotTest
{
    @Test
    public void resolvesRepositoryRelativePaths()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Catalogs/Products.mdo"));

        assertTrue(snapshot.affects(workTree.resolve("project/src/Catalogs/Products.mdo")));
        assertFalse(snapshot.affects(workTree.resolve("project/src/Catalogs/Customers.mdo")));
    }

    @Test
    public void auxiliaryFileAffectsOwningMetadataObject()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Catalogs/Products/ObjectModule.bsl"));

        assertTrue(snapshot.affects(
            workTree.resolve("project/src/Catalogs/Products/Products.mdo")));
        assertFalse(snapshot.affects(
            workTree.resolve("project/src/Catalogs/Customers/Customers.mdo")));
    }

    @Test
    public void flatMetadataFileDoesNotOwnSiblingFiles()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Languages/English.mdo"));

        assertTrue(snapshot.affects(workTree.resolve("project/src/Languages/English.mdo")));
        assertFalse(snapshot.affects(workTree.resolve("project/src/Languages/Russian.mdo")));
    }

    @Test
    public void nestedFormOwnsModuleInItsDirectory()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Catalogs/Products/Forms/ItemForm/Module.bsl"));

        assertTrue(snapshot.affects(
            workTree.resolve("project/src/Catalogs/Products/Forms/ItemForm/Form.form")));
    }

    @Test
    public void metadataFileNextToItsDirectoryOwnsThatDirectory()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Catalogs/Products/ObjectModule.bsl"));

        assertTrue(snapshot.affects(workTree.resolve("project/src/Catalogs/Products.mdo")));
        assertFalse(snapshot.affects(workTree.resolve("project/src/Catalogs/Customers.mdo")));
    }

    @Test
    public void nestedFormFileNextToItsDirectoryIsAffectedByFormContent()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Catalogs/Products/Forms/ItemForm/Form.form"));

        assertTrue(snapshot.affects(
            workTree.resolve("project/src/Catalogs/Products/Forms/ItemForm.mdo")));
        assertFalse(snapshot.affects(
            workTree.resolve("project/src/Catalogs/Products/Forms/ListForm.mdo")));
    }

    @Test
    public void serviceFilesOfConfigurationAreIgnored()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(workTree,
            List.of("SA/src/Configuration/Configuration.distr",
                "SA/src/Configuration/ParentConfigurations.bin"));

        assertFalse(snapshot.hasChanges());
        assertFalse(snapshot.affects(
            workTree.resolve("SA/src/Configuration/Configuration.mdo")));
        assertEquals(0, snapshot.countChangedFilesUnder(workTree.resolve("SA"), 300));
    }

    @Test
    public void serviceFilesDoNotHideRealChanges()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(workTree,
            List.of("SA/src/Configuration/Configuration.distr",
                "SA/src/Catalogs/Products/Products.mdo"));

        assertTrue(snapshot.hasChanges());
        assertTrue(snapshot.affects(
            workTree.resolve("SA/src/Catalogs/Products/Products.mdo")));
        assertEquals(1, snapshot.countChangedFilesUnder(workTree.resolve("SA"), 300));
    }

    @Test
    public void emptySnapshotHasNoChanges()
    {
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            Path.of("workspace", "repository"), List.of());

        assertFalse(snapshot.hasChanges());
    }

    @Test
    public void countsChangedFilesOfRequestedProjectOnly()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(workTree,
            List.of("first/src/Catalogs/Products.mdo", "first/src/Catalogs/Customers.mdo",
                "second/src/Catalogs/Orders.mdo"));

        assertEquals(2, snapshot.countChangedFilesUnder(workTree.resolve("first"), 300));
        assertEquals(1, snapshot.countChangedFilesUnder(workTree.resolve("second"), 300));
    }

    @Test
    public void changedFileCountStopsAboveLimit()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(workTree,
            List.of("project/src/Catalogs/Products.mdo", "project/src/Catalogs/Customers.mdo",
                "project/src/Catalogs/Orders.mdo"));

        assertEquals(2, snapshot.countChangedFilesUnder(workTree.resolve("project"), 1));
    }

    @Test
    public void ownDirectoryOfNestedObjectIsAffectedByItsFiles()
    {
        Path workTree = Path.of("workspace", "repository").toAbsolutePath();
        GitChangeSnapshot snapshot = GitChangeSnapshot.fromRelativePaths(
            workTree, List.of("project/src/Catalogs/Products/Forms/ItemForm/Module.bsl"));

        assertTrue(snapshot.affectsDirectory(
            workTree.resolve("project/src/Catalogs/Products/Forms/ItemForm")));
        assertFalse(snapshot.affectsDirectory(
            workTree.resolve("project/src/Catalogs/Products/Forms/ListForm")));
    }

    @Test
    public void nestedObjectDirectoryFollowsContainmentFeatureGroup()
    {
        Path containerDirectory = Path.of("workspace", "repository", "project", "src", "Catalogs",
            "Products").toAbsolutePath();

        assertEquals(containerDirectory.resolve("Forms").resolve("ItemForm"),
            GitChangeCache.nestedObjectDirectory(containerDirectory, "forms", "ItemForm"));
        assertEquals(containerDirectory.resolve("Templates").resolve("Print"),
            GitChangeCache.nestedObjectDirectory(containerDirectory, "templates", "Print"));
        assertNull(GitChangeCache.nestedObjectDirectory(containerDirectory, "forms", null));
        assertNull(GitChangeCache.nestedObjectDirectory(null, "forms", "ItemForm"));
    }

    @Test
    public void statusRetryUsesBoundedBackoff()
    {
        assertEquals(150, GitChangeCache.retryDelay(0));
        assertEquals(1_000, GitChangeCache.retryDelay(1));
        assertEquals(2_000, GitChangeCache.retryDelay(2));
        assertEquals(60_000, GitChangeCache.retryDelay(20));
    }
}
