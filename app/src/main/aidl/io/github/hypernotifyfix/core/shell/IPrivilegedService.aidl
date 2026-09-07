package io.github.hypernotifyfix.core.shell;
interface IPrivilegedService {
    String execute(in String[] arguments, long timeoutMs);
    int effectiveUid();
    void destroy();
}
