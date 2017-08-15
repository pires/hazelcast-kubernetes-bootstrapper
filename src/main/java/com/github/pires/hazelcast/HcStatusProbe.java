/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.pires.hazelcast;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.cluster.ClusterState;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

class LifecycleListenerImpl implements LifecycleListener {
	private static final Logger logger = LoggerFactory.getLogger(LifecycleListenerImpl.class);

	@Override
	public void stateChanged(LifecycleEvent event) {
		logger.info("LifecycleEvent with new state: {}", event.getState());
	}

}

/**
 * A listener to log the membership status as seen by ConfManager
 */
class MembershipListenerImpl implements MembershipListener {
	private static final Logger logger = LoggerFactory.getLogger(MembershipListenerImpl.class);

	@Override
	public void memberAdded(MembershipEvent membershipEvent) {
		logger.info(membershipEvent.toString());
	}

	@Override
	public void memberRemoved(MembershipEvent membershipEvent) {
		logger.info(membershipEvent.toString());
	}

	@Override
	public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {
		// TODO Auto-generated method stub

	}

}

class ClusterMember {
	public String uuID;
	public String address;

	public ClusterMember(String uuID, String address) {
		this.uuID = uuID;
		this.address = address;
	}

}

@RestController
@RequestMapping(value = "/hc")
public class HcStatusProbe {

	private static final Logger logger = LoggerFactory.getLogger(HcStatusProbe.class);
	ObjectMapper mapper = new ObjectMapper();

	// will be set by other services when the hcInstance got created
	private static HazelcastInstance hcInstance = null;

	public static void setHazelCastInstance(HazelcastInstance hcInstance) {
		HcStatusProbe.hcInstance = hcInstance;

		// add our listeners
		hcInstance.getCluster().addMembershipListener(new MembershipListenerImpl());
		hcInstance.getLifecycleService().addLifecycleListener(new LifecycleListenerImpl());
	}

	// List the cluster members.
	// Optional request parameter "gte" specifies the minimum number of the
	// cluster members seen by the local member,
	// If the cluster size is less than the given 'gte' parameter, return an
	// error response.
	@RequestMapping(path = "/members", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<String> getMembers(@RequestParam(name = "gte", defaultValue = "1") String minMembers)
			throws JsonProcessingException {
		List<ClusterMember> members = new ArrayList<ClusterMember>();

		if (HcStatusProbe.hcInstance == null) {
			logger.error("hazelcast instance not created yet!");
			return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		HcStatusProbe.hcInstance.getCluster().getMembers().forEach(
				member -> members.add(new ClusterMember(member.getUuid(), member.getSocketAddress().toString())));

		int minClusterSize = Integer.parseInt(minMembers);

		String responseBody = mapper.writeValueAsString(members);

		if (members.size() >= minClusterSize) {
			return new ResponseEntity<String>(responseBody, HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(responseBody, HttpStatus.EXPECTATION_FAILED);
		}

	}

	// get the cluster state (e.g. ACTIVE, FROZEN, PASSIVE, IN_TRANSITION)
	@RequestMapping(path = "/state", method = RequestMethod.GET)
	public ResponseEntity<String> getClusterState() {

		if (HcStatusProbe.hcInstance == null) {
			logger.error("hazelcast instance not created yet!");
			return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

		}

		ClusterState clusterState = HcStatusProbe.hcInstance.getCluster().getClusterState();
		logger.info("clusterState is: {}", clusterState);

		if (clusterState == ClusterState.ACTIVE) {
			return new ResponseEntity<String>(clusterState.toString(), HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(clusterState.toString(), HttpStatus.EXPECTATION_FAILED);
		}
	}
}
