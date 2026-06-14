package com.yukisoffd.lyracode.system;

interface ISystemShellService {
    String execute(String command, int timeoutSeconds);
    void destroy();
}
