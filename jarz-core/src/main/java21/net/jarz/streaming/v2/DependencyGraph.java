package net.jarz.streaming.v2;

import java.util.*;

/**
 * Directed graph of class dependencies.
 */
public class DependencyGraph {
    
    private final Set<String> classes = new HashSet<>();
    private final Map<String, Set<String>> outEdges = new HashMap<>(); // class -> dependencies
    private final Map<String, Set<String>> inEdges = new HashMap<>();  // class -> dependents
    
    public void addClass(String className) {
        classes.add(className);
        outEdges.computeIfAbsent(className, k -> new HashSet<>());
        inEdges.computeIfAbsent(className, k -> new HashSet<>());
    }
    
    public void addEdge(String from, String to) {
        addClass(from);
        addClass(to);
        outEdges.get(from).add(to);
        inEdges.get(to).add(from);
    }
    
    public Set<String> classes() {
        return Collections.unmodifiableSet(classes);
    }
    
    public Set<String> dependencies(String className) {
        return outEdges.getOrDefault(className, Collections.emptySet());
    }
    
    public Set<String> dependents(String className) {
        return inEdges.getOrDefault(className, Collections.emptySet());
    }
    
    public int size() {
        return classes.size();
    }
    
    /**
     * Topological sort - superclasses/interfaces before subclasses.
     * Returns classes in dependency order (dependencies first).
     */
    public List<String> topologicalSort() {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (String cls : classes) {
            if (!visited.contains(cls)) {
                topoVisit(cls, visited, visiting, result);
            }
        }
        
        return result;
    }
    
    private void topoVisit(String cls, Set<String> visited, Set<String> visiting, List<String> result) {
        if (visiting.contains(cls)) {
            return; // Cycle - skip
        }
        if (visited.contains(cls)) {
            return;
        }
        
        visiting.add(cls);
        
        for (String dep : dependencies(cls)) {
            if (classes.contains(dep)) {
                topoVisit(dep, visited, visiting, result);
            }
        }
        
        visiting.remove(cls);
        visited.add(cls);
        result.add(cls);
    }
    
    /**
     * Find strongly connected components (classes that depend on each other).
     */
    public List<Set<String>> findStronglyConnectedComponents() {
        // Kosaraju's algorithm
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // First DFS to get finish order
        for (String cls : classes) {
            if (!visited.contains(cls)) {
                dfs1(cls, visited, order);
            }
        }
        
        // Second DFS on reversed graph
        List<Set<String>> sccs = new ArrayList<>();
        visited.clear();
        
        for (int i = order.size() - 1; i >= 0; i--) {
            String cls = order.get(i);
            if (!visited.contains(cls)) {
                Set<String> scc = new HashSet<>();
                dfs2(cls, visited, scc);
                sccs.add(scc);
            }
        }
        
        return sccs;
    }
    
    private void dfs1(String cls, Set<String> visited, List<String> order) {
        visited.add(cls);
        for (String dep : dependencies(cls)) {
            if (classes.contains(dep) && !visited.contains(dep)) {
                dfs1(dep, visited, order);
            }
        }
        order.add(cls);
    }
    
    private void dfs2(String cls, Set<String> visited, Set<String> scc) {
        visited.add(cls);
        scc.add(cls);
        for (String dependent : dependents(cls)) {
            if (!visited.contains(dependent)) {
                dfs2(dependent, visited, scc);
            }
        }
    }
}
