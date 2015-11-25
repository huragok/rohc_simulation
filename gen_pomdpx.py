#!/usr/bin/env python3
from lxml import etree


# Util function definition
def add_entry_to_parameter(parameter, instance_text, table_type, table_text):
    """ Add an Entry element to a Parameter element in the pomdpx file.
    Args:
        parameter (xml element): the parent Parameter element
        instance_text (str): the text of the child Instance element
        table_type (str): either "ProbTable" or "ValueTable"
        table_text (str): the text of the child Table element
   
    Returns:
        xml element: the Entry element added to the Parameter element
    """
    
    entry = etree.SubElement(parameter, 'Entry')
    etree.SubElement(entry, 'Instance').text = instance_text
    etree.SubElement(entry, table_type).text = table_text
    return entry
    
def gen_pomdpx(filename, W, L_B, EPS, GAMMA, len_header_IR, len_header_FO, len_header_SO, len_payload, P_FA = None, P_MD = None):
    """ Generate a .pomdpx file defining the POMDP cross-layer ROHC problem.
    Args:
        filename (str): the filename of the generated file
        W (int): capability of the WLSB coding, the maximal number of packets that can be lost in a row without losing the context synchronization
        L_B (double): the average duration of a sequence of bad states of the Gilbert-Elliott channel
        EPS (double): the average erasure probability of the Gilbert-Elliott channel
        GAMMA (double): the discount factor of the POMDP model.
        len_header_IR (int): length of the IR packet's header
        len_header_FO (int): length of the FO packet's header
        len_header_SO (int): length of the SO packet's header
        len_payload (int): length of the payload
        P_FA (double): the false-alarm probability of the channel state estimator, i.e. the probability when the channel is "good" but the estimation is "bad"
        P_MD (double): the miss-detection probability of the channel state estimator, i.e. the probability when the channel is "bad" but the estimation is "good"
        
    Returns:
        none
        
    References:
    [1] PomdpX File Format (version 1.0), http://bigbird.comp.nus.edu.sg/pmwiki/farm/appl/index.php?n=Main.PomdpXDocumentation
    [2] Hermenier, Romain, Francesco Rossetto, and Matteo Berioli. "On the Behavior of RObust Header Compression U-mode in Channels with Memory." Wireless Communications, IEEE Transactions on 12.8 (2013): 3722-3732.  
    """
    
    # Initialization
    P_BG = 1 / L_B 
    P_GB = P_BG / (1 / EPS - 1)
    P_BB, P_GG = 1 - P_BG, 1 - P_GB

    len_IR = len_header_IR + len_payload
    len_FO = len_header_FO + len_payload
    len_SO = len_header_SO + len_payload
    
    fully_observable = False
    if P_FA is None and P_MD is None:
        fully_observable = True
    P_FA = 0.0 if P_FA is None else P_FA
    P_MD = 0.0 if P_MD is None else P_MD   

    # Outline of the pomdpx file
    ns = 'http://www.w3.org/2001/XMLSchema-instance'
    root_pomdpx = etree.Element('pomdpx', nsmap = {'xsi': ns}, attrib = {'version': '0.1', 'id': 'ROHC', '{{{0}}}noNamespaceSchemaLocation'.format(ns): 'pomdpx.xsd'});

    description = etree.SubElement(root_pomdpx, 'Description')
    discount = etree.SubElement(root_pomdpx, 'Discount')
    variable = etree.SubElement(root_pomdpx, 'Variable')
    initial_state_belief = etree.SubElement(root_pomdpx, 'InitialStateBelief')
    state_transition_function = etree.SubElement(root_pomdpx, 'StateTransitionFunction')
    obs_function = etree.SubElement(root_pomdpx, 'ObsFunction')
    reward_function = etree.SubElement(root_pomdpx, 'RewardFunction')

    # Description
    description.text = "Cross-layer ROHC design problem using estimated channel state. W = {0}; L_B = {1}, EPS = {2}; P_FA = {3}, P_MD = {4}; gamma = {5}.".format(W, L_B, EPS, P_FA, P_MD, GAMMA)

    # Discount
    discount.text = str(GAMMA)

    # Variable
    if not fully_observable:
        state_var = etree.SubElement(variable, 'StateVar', attrib = {'vnamePrev': 'state_0', 'vnameCurr': 'state_1'})
    else:
        state_var = etree.SubElement(variable, 'StateVar', attrib = {'vnamePrev': 'state_0', 'vnameCurr': 'state_1', 'fullyObs': 'true'})
        
    etree.SubElement(state_var, 'NumValues').text = str(4 + W) # The states are defined in the order of NC_B, NC_G, SC_B, SC_G, FC_0, FC_1, ..., FC_{W - 1}
    
    if not fully_observable:
        obs_var = etree.SubElement(variable, 'ObsVar', attrib = {'vname': 'est_channel'})
        etree.SubElement(obs_var, 'ValueEnum').text = "obad ogood"

    action_var = etree.SubElement(variable, 'ActionVar', attrib = {'vname': 'type_compression'})
    etree.SubElement(action_var, 'ValueEnum').text = "IR FO SO"

    reward_var = etree.SubElement(variable, 'RewardVar', attrib = {'vname': 'efficiency'})

    # Initial state belief
    cp_isb = etree.SubElement(initial_state_belief, 'CondProb')
    etree.SubElement(cp_isb, 'Var').text = "state_0"
    etree.SubElement(cp_isb, 'Parent').text = "null"
    param_cp_isb = etree.SubElement(cp_isb, 'Parameter', attrib = {'type': 'TBL'})
    add_entry_to_parameter(param_cp_isb, "-", 'ProbTable', ' '.join(list(map(str, [EPS, 1-EPS] + [0] * (2 + W)))))

    # State transition function
    cp_stf = etree.SubElement(state_transition_function, 'CondProb')
    etree.SubElement(cp_stf, 'Var').text = "state_1"
    etree.SubElement(cp_stf, 'Parent').text = "state_0 type_compression" # s, a, s'
    param_cp_stf = etree.SubElement(cp_stf, 'Parameter', attrib = {'type': 'TBL'})

    ## The action-independent state transition probability
    add_entry_to_parameter(param_cp_stf, "s0 * s0", 'ProbTable', str(P_BB))
    add_entry_to_parameter(param_cp_stf, "s1 * s0", 'ProbTable', str(P_GB))
    add_entry_to_parameter(param_cp_stf, "s4 * s4", 'ProbTable', str(P_GG))
    add_entry_to_parameter(param_cp_stf, "s4 * s5", 'ProbTable', str(P_GB))
    add_entry_to_parameter(param_cp_stf, "s2 * s2", 'ProbTable', str(P_BB))
    add_entry_to_parameter(param_cp_stf, "s3 * s2", 'ProbTable', str(P_GB))

    for w in range(1, W):
        add_entry_to_parameter(param_cp_stf, "s{0} * s4".format(w + 4), 'ProbTable', str(P_BG))
        
    for w in range(1, W - 1):
        add_entry_to_parameter(param_cp_stf, "s{0} * s{1}".format(w + 4, w + 5), 'ProbTable', str(P_BB))
    add_entry_to_parameter(param_cp_stf, "s{0} * s2".format(W + 3), 'ProbTable', str(P_BB))

    ## The action-dependent state transition probability
    ### IR
    add_entry_to_parameter(param_cp_stf, "s0 IR s4", 'ProbTable', str(P_BG))
    add_entry_to_parameter(param_cp_stf, "s1 IR s4", 'ProbTable', str(P_GG))
    add_entry_to_parameter(param_cp_stf, "s2 IR s4", 'ProbTable', str(P_BG))
    add_entry_to_parameter(param_cp_stf, "s3 IR s4", 'ProbTable', str(P_GG))

    ### FO
    add_entry_to_parameter(param_cp_stf, "s0 FO s1", 'ProbTable', str(P_BG))
    add_entry_to_parameter(param_cp_stf, "s1 FO s1", 'ProbTable', str(P_GG))
    add_entry_to_parameter(param_cp_stf, "s2 FO s4", 'ProbTable', str(P_BG))
    add_entry_to_parameter(param_cp_stf, "s3 FO s4", 'ProbTable', str(P_GG))

    ### SO
    add_entry_to_parameter(param_cp_stf, "s0 SO s1", 'ProbTable', str(P_BG))
    add_entry_to_parameter(param_cp_stf, "s1 SO s1", 'ProbTable', str(P_GG))
    add_entry_to_parameter(param_cp_stf, "s2 SO s3", 'ProbTable', str(P_BG))
    add_entry_to_parameter(param_cp_stf, "s3 SO s3", 'ProbTable', str(P_GG))

    # Observation function
    if not fully_observable:
        cp_of = etree.SubElement(obs_function, 'CondProb')
        etree.SubElement(cp_of, 'Var').text = "est_channel"
        etree.SubElement(cp_of, 'Parent').text = "state_1" # s', o
        param_cp_of = etree.SubElement(cp_of, 'Parameter', attrib = {'type': 'TBL'})

        state_B = ['s0', 's2'] + ['s' + str(w + 4) for w in range(1, W)]
        state_G = ['s1', 's3', 's4']

        ## BB
        for state in state_B:
            add_entry_to_parameter(param_cp_of, state + ' obad', 'ProbTable', str(1 - P_MD))
            
        ## BG
        for state in state_B:
            add_entry_to_parameter(param_cp_of, state + ' ogood', 'ProbTable', str(P_MD))

        ## GB
        for state in state_G:
            add_entry_to_parameter(param_cp_of, state + ' obad', 'ProbTable', str(P_FA))

        ## GG
        for state in state_G:
            add_entry_to_parameter(param_cp_of, state + ' ogood', 'ProbTable', str(1 - P_FA))

    # Reward function
    f_rf = etree.SubElement(reward_function, 'Func')
    etree.SubElement(f_rf, 'Var').text = "efficiency" 
    etree.SubElement(f_rf, 'Parent').text = "type_compression state_1" # a, s'
    param_f_rf = etree.SubElement(f_rf, 'Parameter', attrib = {'type': 'TBL'})

    ## Only when s' = FC_0 there is a non-zero reward
    add_entry_to_parameter(param_f_rf, "- s4", 'ValueTable', ' '.join(map(str, [len_payload / len_IR, len_payload / len_FO, len_payload / len_SO])))

    # Generate the .pomdpx file
    tree_pomdpx = etree.ElementTree(root_pomdpx)
    with open(filename, 'wb') as output_file:
        tree_pomdpx.write(output_file, pretty_print=True, encoding="iso-8859-1")     

if __name__ == "__main__":
    filename = "instance.pomdpx"
    W = 8 # Capability of the WLSB coding
    L_B = 8 # Average duration of a sequence of consecutive bad states
    EPS = 0.2 # Average deletion probability

    P_FA = 0.1 # False alarm probability
    P_MD = 0.1 # Miss detection probability

    GAMMA = 0.95 # The discount factor

    len_header_IR = 80
    len_header_FO = 16
    len_header_SO = 4
    len_payload = 20

    gen_pomdpx(filename, W, L_B, EPS, GAMMA, len_header_IR, len_header_FO, len_header_SO, len_payload, P_FA, P_MD)
    #gen_pomdpx(filename, W, L_B, EPS, GAMMA, len_header_IR, len_header_FO, len_header_SO, len_payload)
