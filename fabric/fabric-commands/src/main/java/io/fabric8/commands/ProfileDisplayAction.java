/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.commands;

import io.fabric8.api.Constants;
import io.fabric8.api.Container;
import io.fabric8.api.FabricService;
import io.fabric8.api.Profile;
import io.fabric8.api.ProfileService;
import io.fabric8.api.Version;
import io.fabric8.utils.FabricValidations;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.CompleterValues;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;

import com.google.common.base.Charsets;

@Command(name = "profile-display", scope = "fabric", description = "Displays information about the specified version of the specified profile (where the version defaults to the current default version)")
public class ProfileDisplayAction extends AbstractAction {

    @Option(name = "--version", description = "Select a specific profile version. Defaults to the current default version.")
    private String versionId;
    @Option(name = "--overlay", aliases = "-o", description = "Shows the effective profile settings, taking into account the settings inherited from parent profiles.")
    private Boolean overlay = false;
    @Option(name = "--display-resources", aliases = "-r", description = "Displays the content of additional profile resources.")
    private Boolean displayResources = false;
    @Argument(index = 0, required = true, name = "profile", description = "The name of the profile.")
    @CompleterValues(index = 0)
    private String profileId;

    private final FabricService fabricService;
    private final ProfileService profileService;

    ProfileDisplayAction(FabricService fabricService) {
        this.fabricService = fabricService;
        this.profileService = fabricService.adapt(ProfileService.class);
    }

    public FabricService getFabricService() {
        return fabricService;
    }

    @Override
    protected Object doExecute() throws Exception {
        FabricValidations.validateProfileName(profileId);
        Version version = versionId != null ? profileService.getRequiredVersion(versionId) : fabricService.getRequiredDefaultVersion();
        for (Profile profile : version.getProfiles()) {
            if (profileId.equals(profile.getId())) {
                displayProfile(profile);
            }
        }
        return null;
    }

    private String toString(Container[] containers) {
        StringBuffer rc = new StringBuffer();
        for (Container container : containers) {
            rc.append(container.getId());
            rc.append(" ");
        }
        return rc.toString().trim();
    }

    private static void printConfigList(String header, PrintStream out, List<String> list) {
        out.println(header);
        for (String str : list) {
            out.printf("\t%s\n", str);
        }
        out.println();
    }

    private void displayProfile(Profile profile) {
        PrintStream output = session.getConsole();

        output.println("Profile id: " + profile.getId());
        output.println("Version   : " + profile.getVersion());

        output.println("Attributes: ");
        Map<String, String> props = profile.getAttributes();
        for (String key : props.keySet()) {
            output.println("\t" + key + ": " + props.get(key));
        }

    	String versionId = profile.getVersion();
    	String profileId = profile.getId();
        output.printf("Containers: %s\n", toString(fabricService.getAssociatedContainers(versionId, profileId)));

        ProfileService profileService = fabricService.adapt(ProfileService.class);
        if (overlay) {
            profile = profileService.getOverlayProfile(profile);
        }

        Map<String, Map<String, String>> configuration = new HashMap<>(profile.getConfigurations());
        Map<String, byte[]> resources = profile.getFileConfigurations();
        Map<String,String> agentConfiguration = profile.getConfiguration(Constants.AGENT_PID);
        List<String> agentProperties = new ArrayList<String>();
        List<String> systemProperties = new ArrayList<String>();
        List<String> configProperties = new ArrayList<String>();
        List<String> otherResources = new ArrayList<String>();
        for (Map.Entry<String, String> entry : agentConfiguration.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value.contains(",")) {
                value = "\t" + value.replace(",", ",\n\t\t");
            }

            if (key.startsWith("system.")) {
                systemProperties.add("  " + key.substring("system.".length()) + " = " + value);
            }
            else if (key.startsWith("config.")) {
                configProperties.add("  " + key.substring("config.".length()) + " = " + value);
            }
            else if (!key.startsWith("feature.") && !key.startsWith("repository") &&
                        !key.startsWith("bundle.") && !key.startsWith("fab.") &&
                        !key.startsWith("override.") && !key.startsWith("attribute.")) {
                agentProperties.add("  " + key + " = " + value);
            }
        }

        if (configuration.containsKey(Constants.AGENT_PID)) {
            output.println("\nContainer settings");
            output.println("----------------------------");

            if (profile.getLibraries().size() > 0) {
                printConfigList("Libraries : ", output, profile.getLibraries());
            }
            if (profile.getRepositories().size() > 0) {
                printConfigList("Repositories : ", output, profile.getRepositories());
            }
            if (profile.getFeatures().size() > 0) {
                printConfigList("Features : ", output, profile.getFeatures());
            }
            if (profile.getBundles().size() > 0) {
                printConfigList("Bundles : ", output, profile.getBundles());
            }
            if (profile.getFabs().size() > 0) {
                printConfigList("Fabs : ", output, profile.getFabs());
            }
            if (profile.getOverrides().size() > 0) {
                printConfigList("Overrides : ", output, profile.getOverrides());
            }

            if (agentProperties.size() > 0) {
                printConfigList("Agent Properties : ", output, agentProperties);
            }

            if (systemProperties.size() > 0) {
                printConfigList("System Properties : ", output, systemProperties);
            }

            if (configProperties.size() > 0) {
                printConfigList("Config Properties : ", output, configProperties);
            }

            configuration.remove(Constants.AGENT_PID);
        }

        output.println("\nConfiguration details");
        output.println("----------------------------");
        for (Map.Entry<String, Map<String, String>> cfg : configuration.entrySet()) {
            output.println("PID: " + cfg.getKey());

            for (Map.Entry<String, String> values : cfg.getValue().entrySet()) {
                output.println("  " + values.getKey() + " " + values.getValue());
            }
            output.println("\n");
        }

        output.println("\nOther resources");
        output.println("----------------------------");
        for (Map.Entry<String,byte[]> resource : resources.entrySet()) {
            String name = resource.getKey();
            if (!name.endsWith(".properties")) {
                output.println("Resource: " + resource.getKey());
                if (displayResources) {
                    output.println(new String(resource.getValue(), Charsets.UTF_8));

                    output.println("\n");
                }
            }
        }
    }

}
