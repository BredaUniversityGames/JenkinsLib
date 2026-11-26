// Compiler configuration for src/: Jenkins auto-imports @NonCPS into
// shared-library code, so do the same here.
withConfig(configuration) {
    imports {
        normal 'com.cloudbees.groovy.cps.NonCPS'
    }
}
