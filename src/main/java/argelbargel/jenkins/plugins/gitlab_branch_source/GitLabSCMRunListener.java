package argelbargel.jenkins.plugins.gitlab_branch_source;


import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMAcceptMergeRequestAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMCauseAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMHeadMetadataAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.actions.GitLabSCMPublishAction;
import argelbargel.jenkins.plugins.gitlab_branch_source.heads.GitLabSCMHead;
import argelbargel.jenkins.plugins.gitlab_branch_source.heads.GitLabSCMMergeRequestHead;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMSource;

import javax.annotation.Nonnull;

import static hudson.model.Result.SUCCESS;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;


@SuppressWarnings("unused")
@Extension
public class GitLabSCMRunListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(GitLabSCMRunListener.class.getName());

    @Override
    public void onStarted(Run<?, ?> build, TaskListener listener) {
        GitLabSCMHeadMetadataAction metadata = getMetadataAction(build);
        GitLabSCMPublishAction publishAction = build.getParent().getAction(GitLabSCMPublishAction.class);
        if (metadata != null && publishAction != null) {
            GitLabSCMCauseAction cause = build.getAction(GitLabSCMCauseAction.class);
            String description = (cause != null) ? cause.getDescription() : "";
            publishAction.updateBuildDescription(build, description, listener);
            publishAction.publishStarted(build, metadata, description);
        }
    }

    @Override
    public void onCompleted(Run<?, ?> build, @Nonnull TaskListener listener) {
        GitLabSCMHeadMetadataAction metadata = getMetadataAction(build);
        GitLabSCMPublishAction publishAction = build.getParent().getAction(GitLabSCMPublishAction.class);
        if (metadata != null && publishAction != null) {
            publishAction.publishResult(build, metadata);
        }

        if (build.getResult() == SUCCESS) {
            GitLabSCMAcceptMergeRequestAction acceptAction = build.getParent().getAction(GitLabSCMAcceptMergeRequestAction.class);
            if (acceptAction != null) {
                acceptAction.acceptMergeRequest(build, listener);
            }
        }
    }

    private GitLabSCMHeadMetadataAction getMetadataAction(Run<?, ?> build) {
        GitLabSCMHeadMetadataAction metadata = build.getAction(GitLabSCMHeadMetadataAction.class);
        if (metadata != null)
            return metadata;

        Job<?, ?> job = build.getParent();
        metadata = build.getParent().getAction(GitLabSCMHeadMetadataAction.class);
        if(metadata != null)
            return metadata;

        GitLabSCMSource src = (GitLabSCMSource) SCMSource.SourceByItem.findSource(job);
        if(src == null)
            return null;

        BranchJobProperty branchJob = job.getProperty(BranchJobProperty.class);
        GitLabSCMHead head = (GitLabSCMHead) branchJob.getBranch().getHead();
        AbstractGitSCMSource.SCMRevisionImpl revision = head.getRevision();
        String name = head.getName();
        GitLabSCMHead branch = head instanceof GitLabSCMMergeRequestHead ? ((GitLabSCMMergeRequestHead) head).getSource() : head;
        int projectId = branch.getProjectId();
        String branchName = branch.getName();
        String hash = revision.getHash();
        String url = src.getProject().getWebUrl() + "/" + "commits/" + hash;
        metadata = new GitLabSCMHeadMetadataAction(name, projectId, branchName, hash, url);
        try {
            job.addAction(metadata);
            job.save();
        } catch (IOException ex) {
            // ignore error
            LOGGER.log(Level.SEVERE, "cannot save job", ex);
        }

        return metadata;
    }
}
