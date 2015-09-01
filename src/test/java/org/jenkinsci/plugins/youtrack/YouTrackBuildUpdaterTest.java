package org.jenkinsci.plugins.youtrack;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class YouTrackBuildUpdaterTest {
    @Test
    public void testYouTrackNotConfigured() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildListener listener = mock(BuildListener.class);
        Launcher launcher = mock(Launcher.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(listener.getLogger()).thenReturn(new PrintStream(stream));
        YouTrackBuildUpdater youTrackBuildUpdater = spy(new YouTrackBuildUpdater(null, "Build Bundle", "${BUILD_NUMBER}", false, false, false));
        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(false);
        doReturn(youTrackSite).when(youTrackBuildUpdater).getYouTrackSite(build);
        youTrackBuildUpdater.perform(build, launcher, listener);

        assertThat(stream.toString().trim(), equalTo("No YouTrack site configured"));
    }

    @Test
    public void testNoIssuesToFixAndShouldNotCreateBuild() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildListener listener = mock(BuildListener.class);
        Launcher launcher = mock(Launcher.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(listener.getLogger()).thenReturn(new PrintStream(stream));
        YouTrackBuildUpdater youTrackBuildUpdater = spy(new YouTrackBuildUpdater(null, "Build Bundle", "${BUILD_NUMBER}", false, true, false));
        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(true);
        doReturn(youTrackSite).when(youTrackBuildUpdater).getYouTrackSite(build);
        youTrackBuildUpdater.perform(build, launcher, listener);
        assertThat(stream.toString().trim(), equalTo("No build to add"));
    }

    @Test
    public void testNoIssuesToFixButShouldCreateBuild() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildListener listener = mock(BuildListener.class);
        Launcher launcher = mock(Launcher.class);
        YouTrackServer youTrackServer = mock(YouTrackServer.class);

        doReturn(new EnvVars()).when(build).getEnvironment(listener);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(listener.getLogger()).thenReturn(new PrintStream(stream));
        YouTrackBuildUpdater youTrackBuildUpdater = spy(new YouTrackBuildUpdater(null, "Build Bundle", "${BUILD_NUMBER}", false, false, false));
        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(true);

        doReturn(youTrackSite).when(youTrackBuildUpdater).getYouTrackSite(build);

        when(youTrackBuildUpdater.getYouTrackServer(youTrackSite)).thenReturn(youTrackServer);
        User user = new User();
        Command command = new Command();
        command.setStatus(Command.Status.OK);
        when(youTrackServer.addBuildToBundle("site", user, "Build Bundle", "${BUILD_NUMBER}")).thenReturn(command);
        user.setUsername("user");
        user.setLoggedIn(true);
        doReturn(user).when(youTrackServer).login("user","password");
        youTrackBuildUpdater.perform(build, launcher, listener);

        assertThat(stream.toString().trim(), equalTo("Added build ${BUILD_NUMBER} to bundle: Build Bundle"));
    }

    @Test
    public void testIssueToFixAndShouldCreateBuild() throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildListener listener = mock(BuildListener.class);
        Launcher launcher = mock(Launcher.class);
        YouTrackServer youTrackServer = mock(YouTrackServer.class);

        doReturn(new EnvVars()).when(build).getEnvironment(listener);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        when(listener.getLogger()).thenReturn(new PrintStream(stream));
        when(build.getResult()).thenReturn(Result.SUCCESS);
        YouTrackBuildUpdater youTrackBuildUpdater = spy(new YouTrackBuildUpdater(null, "Build Bundle", "${BUILD_NUMBER}", false, false, false));
        YouTrackSite youTrackSite = new YouTrackSite("site", "user", "password", "http://example.com");
        youTrackSite.setPluginEnabled(true);

        doReturn(youTrackSite).when(youTrackBuildUpdater).getYouTrackSite(build);
        ArrayList<Issue> issues = new ArrayList<Issue>();
        Issue e = new Issue("ISSUE-1");
        issues.add(e);
        YouTrackSaveFixedIssues youTrackSaveFixedIssues = new YouTrackSaveFixedIssues(issues);
        when(build.getAction(YouTrackSaveFixedIssues.class)).thenReturn(youTrackSaveFixedIssues);

        when(youTrackBuildUpdater.getYouTrackServer(youTrackSite)).thenReturn(youTrackServer);
        User user = new User();
        Command command = new Command();
        command.setStatus(Command.Status.OK);
        when(youTrackServer.addBuildToBundle("site", user, "Build Bundle", "${BUILD_NUMBER}")).thenReturn(command);
        Command command1 = new Command();
        command1.setStatus(Command.Status.OK);
        when(youTrackServer.applyCommand("site", user, e, "fixedinbuild ${BUILD_NUMBER}", null, null, null, true)).thenReturn(command1);
        user.setUsername("user");
        user.setLoggedIn(true);
        doReturn(user).when(youTrackServer).login("user","password");
        youTrackBuildUpdater.perform(build, launcher, listener);

        assertThat(stream.toString().replace("\r\n","\n"), equalTo("Added build ${BUILD_NUMBER} to bundle: Build Bundle\nUpdated Fixed in build to ${BUILD_NUMBER} for ISSUE-1\n"));
    }
}