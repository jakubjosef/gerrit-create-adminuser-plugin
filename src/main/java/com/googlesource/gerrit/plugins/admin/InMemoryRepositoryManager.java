package com.googlesource.gerrit.plugins.admin;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;

import java.util.Map;
import java.util.SortedSet;

/** Repository manager that uses in-memory repositories. */
public class InMemoryRepositoryManager implements GitRepositoryManager {
    public static InMemoryRepository newRepository(Project.NameKey name) {
        return new Repo(name);
    }

    public static class Description extends DfsRepositoryDescription {
        private final Project.NameKey name;
        private String desc;

        private Description(Project.NameKey name) {
            super(name.get());
            this.name = name;
            desc = "In-memory repository " + name.get();
        }

        public Project.NameKey getProject() {
            return name;
        }
    }

    public static class Repo extends InMemoryRepository {
        private Repo(Project.NameKey name) {
            super(new Description(name));
        }

        @Override
        public Description getDescription() {
            return (Description) super.getDescription();
        }
    }

    private Map<String, Repo> repos = Maps.newHashMap();

    @Override
    public synchronized Repo openRepository(Project.NameKey name)
            throws RepositoryNotFoundException {
        return get(name);
    }

    @Override
    public synchronized Repo createRepository(Project.NameKey name)
            throws RepositoryCaseMismatchException, RepositoryNotFoundException {
        Repo repo;
        try {
            repo = get(name);
            if (!repo.getDescription().getRepositoryName().equals(name.get())) {
                throw new RepositoryCaseMismatchException(name);
            }
        } catch (RepositoryNotFoundException e) {
            repo = new Repo(name);
            repos.put(name.get().toLowerCase(), repo);
        }
        return repo;
    }

    @Override
    public synchronized Repo openMetadataRepository(
            Project.NameKey name) throws RepositoryNotFoundException {
        return openRepository(name);
    }

    @Override
    public synchronized SortedSet<Project.NameKey> list() {
        SortedSet<Project.NameKey> names = Sets.newTreeSet();
        for (DfsRepository repo : repos.values()) {
            names.add(new Project.NameKey(repo.getDescription().getRepositoryName()));
        }
        return ImmutableSortedSet.copyOf(names);
    }

    @Override
    public synchronized String getProjectDescription(Project.NameKey name)
            throws RepositoryNotFoundException {
        return get(name).getDescription().desc;
    }

    @Override
    public synchronized void setProjectDescription(Project.NameKey name,
                                                   String description) {
        try {
            get(name).getDescription().desc = description;
        } catch (RepositoryNotFoundException e) {
            // Ignore.
        }
    }

    private synchronized Repo get(Project.NameKey name)
            throws RepositoryNotFoundException {
        Repo repo = repos.get(name.get().toLowerCase());
        if (repo != null) {
            return repo;
        } else {
            throw new RepositoryNotFoundException(name.get());
        }
    }
}
