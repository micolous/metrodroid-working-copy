//
//  SupportedCardsHeader.swift
//  metrodroid
//
//  Created by Vladimir Serbinenko on 29.08.19.
//  Copyright © 2019 Vladimir. All rights reserved.
//

import Foundation
import UIKit

class SupportedCardsHeader : UICollectionReusableView {
    @IBOutlet private weak var label: UILabel!
    @IBOutlet private weak var arrow: UIImageView!
    private var delegate: SupportedCardsViewController? = nil
    private var section: Int = 0
    
    func additionalElements() {
        addGestureRecognizer(UITapGestureRecognizer(target: self,
                                                    action: #selector(tapHeader)))
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        additionalElements()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        additionalElements()
    }
    
    func setExpansion(expanded: Bool) {
        self.arrow.image = UIImage(named: expanded ? "expanded_header" : "collapsed_header")
    }
    
    @objc func tapHeader() {
        setExpansion(expanded: delegate?.toggleSection(sectionNumber: section) ?? false)
    }
    
    func setState(title: String, delegate: SupportedCardsViewController, section: Int,
                  expanded: Bool) {
        self.label.text = title
        self.delegate = delegate
        self.section = section
        setExpansion(expanded: expanded)
    }
}
