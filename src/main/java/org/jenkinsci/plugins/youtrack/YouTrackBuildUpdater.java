package org.jenkinsci.plugins.youtrack;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.youtrack.youtrackapi.BuildBundle;
import org.jenkinsci.plugins.youtrack.youtrackapi.Issue;
import org.jenkinsci.plugins.youtrack.youtrackapi.User;
import org.jenkinsci.plugins.youtrack.youtrackapi.YouTrackServer;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;

/**
 * Updates build bundle.
 */
public class YouTrackBuildUpdater extends Recorder {

    /**
     * This was the name, where there was an implicit ${BUILD_NUMBER} (name) format.
     * @deprecated {@link #buildName} should be used instead.
     */
    private String name;
    /**
     * Name of build to create and use for setting Fixed in build.
     */
    @Setter private String buildName;
    @Getter @Setter private String bundleName;
    @Getter @Setter private boolean markFixedIfUnstable;
    @Getter @Setter private boolean onlyAddIfHasFixedIssues;
    @Getter @Setter private boolean runSilently;

    @DataBoundConstructor
    public YouTrackBuildUpdater(String name, String bundleName, String buildName, boolean markFixedIfUnstable, boolean onlyAddIfHasFixedIssues, boolean runSilently) {
        this.name = name;
        this.bundleName = bundleName;

        this.buildName = buildName;
        this.markFixedIfUnstable = markFixedIfUnstable;
        this.onlyAddIfHasFixedIssues = onlyAddIfHasFixedIssues;
        this.runSilently = runSilently;
    }

    @Deprecated
    public String getName() {
        return name;
    }

    @Deprecated
    public void setName(String name) {
        this.name = name;
    }

    public String getBuildName() {
        if (name != null && buildName == null) {
            this.buildName = "${BUILD_NUMBER} ("+name+")";
        }
        return buildName;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        YouTrackSite youTrackSite = getYouTrackSite(build);
        if (youTrackSite == null || !youTrackSite.isPluginEnabled()) {
            listener.getLogger().println("No YouTrack site configured");
            return true;
        }


        YouTrackSaveFixedIssues action = build.getAction(YouTrackSaveFixedIssues.class);

        YouTrackCommandAction youTrackCommandAction = build.getAction(YouTrackCommandAction.class);
        if(youTrackCommandAction == null) {
            youTrackCommandAction = new YouTrackCommandAction(build);
            build.addAction(youTrackCommandAction);
        }

        //Return early if there is no build to be added
        if(onlyAddIfHasFixedIssues) {
            if(action == null || action.getIssueIds().isEmpty()) {
                listener.getLogger().println("No build to add");
                return true;
            }
        }

        YouTrackServer youTrackServer = getYouTrackServer(youTrackSite);
        User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
        if(user == null || !user.isLoggedIn()) {
            listener.getLogger().println("FAILED: to log in to youtrack");
            youTrackSite.failed(build);
            return true;
        }
        EnvVars environment = build.getEnvironment(listener);
        String buildName;
        if(getBuildName() == null || getBuildName().equals("")) {
            buildName = String.valueOf(build.getNumber());
        } else {

            buildName = environment.expand(getBuildName());

        }
        String inputBundleName =environment.expand(getBundleName());

        Command addedBuild = youTrackServer.addBuildToBundle(youTrackSite.getName(), user, inputBundleName, buildName);
        if(addedBuild.getStatus() == Command.Status.OK) {
            listener.getLogger().println("Added build " + buildName + " to bundle: " + inputBundleName);
        } else {
            listener.getLogger().println("FAILED: adding build " + buildName + " to bundle: " + inputBundleName);
            youTrackSite.failed(build);
        }

        youTrackCommandAction.addCommand(addedBuild);

        if(action != null) {
            List<String> issueIds = action.getIssueIds();
            boolean stable = build.getResult().isBetterOrEqualTo(Result.SUCCESS);
            boolean unstable = build.getResult().isBetterOrEqualTo(Result.UNSTABLE);


            if(stable || (isMarkFixedIfUnstable() && unstable)) {

                for (String issueId : issueIds) {
                Issue issue = new Issue(issueId);

                    String commandValue = "fixedinbuild " + buildName; // alias to "fixed in build"
                    Command command = youTrackServer.applyCommand(youTrackSite.getName(), user, issue, commandValue, null, null, null, !runSilently);
                    if(command.getStatus() == Command.Status.OK) {
                        listener.getLogger().println("Updated Fixed in build to " + buildName + " for " + issueId);
                    } else {
                        youTrackSite.failed(build);
                        listener.getLogger().println("FAILED: updating Fixed in build to " + buildName + " for " + issueId);
                    }
                    youTrackCommandAction.addCommand(command);
                }
            }

        }

        return true;
    }

    YouTrackServer getYouTrackServer(YouTrackSite youTrackSite) {
        return new YouTrackServer(youTrackSite.getUrl());
    }

    YouTrackSite getYouTrackSite(AbstractBuild<?, ?> build) {
        return YouTrackSite.get(build.getProject());
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "YouTrack Build Updater";
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(YouTrackBuildUpdater.class, formData);
        }

        @SuppressWarnings("UnusedDeclaration")
        public AutoCompletionCandidates doAutoCompleteBundleName(@AncestorInPath AbstractProject project, @QueryParameter String value) {
            YouTrackSite youTrackSite = YouTrackSite.get(project);
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
            if(youTrackSite != null) {
                YouTrackServer youTrackServer = new YouTrackServer(youTrackSite.getUrl());
                User user = youTrackServer.login(youTrackSite.getUsername(), youTrackSite.getPassword());
                if(user != null) {
                    List<BuildBundle> bundles = youTrackServer.getBuildBundles(user);
                    for (BuildBundle bundle : bundles) {
                        if(bundle.getName().toLowerCase().contains(value.toLowerCase())) {
                            autoCompletionCandidates.add(bundle.getName());
                        }
                    }
                }
            }
            return autoCompletionCandidates;
        }
    }
}
