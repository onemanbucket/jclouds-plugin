package jenkins.plugins.jclouds.compute;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Jenkins Slave node  - managed by JClouds.
 *
 * @author Vijay Kiran
 */
public class JCloudsSlave extends Slave {
    private static final Logger LOGGER = Logger.getLogger(JCloudsSlave.class.getName());
    private NodeMetadata nodeMetaData;
    public final boolean stopOnTerminate;
    private String cloudName;
    
   @DataBoundConstructor
   public JCloudsSlave(String cloudName,
                       String name,
                       String nodeDescription,
                       String remoteFS,
                       String numExecutors,
                       Mode mode,
                       String labelString,
                       ComputerLauncher launcher,
                       RetentionStrategy retentionStrategy,
                       List<? extends NodeProperty<?>> nodeProperties,
                       boolean stopOnTerminate) throws Descriptor.FormException, IOException {
      super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
      this.stopOnTerminate = stopOnTerminate;
      this.cloudName = cloudName;
   }

    /**
     * Constructs a new slave from JCloud's NodeMetadata
     *
     * @param cloudName - the name of the cloud that's provisioning this slave.
     * @param metadata - JCloudsNodeMetadata
     * @param labelString - Label(s) for this slave.
     * @param description - Description of this slave.
     * @param numExecutors - Number of executors for this slave.
     * @param stopOnTerminate - if true, suspend the slave rather than terminating it.
     * @throws IOException
     * @throws Descriptor.FormException
     */
    public JCloudsSlave(final String cloudName, NodeMetadata metadata, final String labelString,
                        final String description, final String numExecutors,
                        final boolean stopOnTerminate) throws IOException, Descriptor.FormException {
        this(cloudName,
             metadata.getName(),
             description,
             "/jenkins",
             numExecutors,
             Mode.NORMAL,
             labelString,
             new JCloudsLauncher(),
             new JCloudsRetentionStrategy(),
             Collections.<NodeProperty<?>>emptyList(),
             stopOnTerminate);
        this.nodeMetaData = metadata;
    }

   /**
    * Get Jclouds NodeMetadata associated with this Slave.
    *
    * @return {@link NodeMetadata}
    */
   public NodeMetadata getNodeMetaData() {
      return nodeMetaData;
   }

    /**
     * Get the JClouds profile identifier for the Cloud associated with this slave.
     *
     * @return cloudName
     */
    public String getCloudName() {
        return cloudName;
    }
    
   /**
    * {@inheritDoc}
    */
   @Override
   public Computer createComputer() {
      LOGGER.info("Creating a new JClouds Slave");
      return new JCloudsComputer(this);
   }

   /**
    * Destroy the node calls {@link ComputeService#destroyNode}
    *
    */
   public void terminate() {
       final ComputeService compute = JCloudsCloud.getByName(cloudName).getCompute();
       if (compute.getNodeMetadata(nodeMetaData.getId()) != null &&
           compute.getNodeMetadata(nodeMetaData.getId()).getState().equals(NodeState.RUNNING)) {
           if (stopOnTerminate) {
               LOGGER.info("Suspending the Slave : " + getNodeName());
               compute.suspendNode(nodeMetaData.getId());
           } else {
               LOGGER.info("Terminating the Slave : " + getNodeName());
               compute.destroyNode(nodeMetaData.getId());
           }
       } else {
           LOGGER.info("Slave " + getNodeName() + " is already not running.");
       }
   }


   @Extension
   public static final class JCloudsSlaveDescriptor extends SlaveDescriptor {

      @Override
      public String getDisplayName() {
         return "JClouds Slave";
      }


      /**
       * {@inheritDoc}
       */
      @Override
      public boolean isInstantiable() {
         return false;
      }
   }
}
